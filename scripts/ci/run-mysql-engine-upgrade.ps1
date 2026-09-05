[CmdletBinding()]
param(
  [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path,
  [string]$TargetImage,
  [switch]$KeepResources
)
$ErrorActionPreference = 'Stop'
$sourceImage = 'mysql:8.0@sha256:7dcddc01f13bab2f15cde676d44d01f61fc9f99fe7785e86196dfc07d358ae2b'
$checkerImage = 'container-registry.oracle.com/mysql/community-server:8.4@sha256:7dcc4add9183664de3a214daf85a50c3ba6cccfd7534f700b6561bf5b41885be'
$database = 'cgc_pms_m100_test'
$batch = 'cgc-pms-m100-engine-' + [guid]::NewGuid().ToString('N').Substring(0,12)
$network = "$batch-net"
$artifacts = Join-Path $RepoRoot "backend/target/mysql-engine-upgrade/$batch"
$containers = [Collections.Generic.List[string]]::new()
$volumes = [Collections.Generic.List[string]]::new()
$networkCreated = $false
$environment = @{}
foreach ($key in @('MYSQL_ROOT_PASSWORD','MYSQL_ROOT_HOST','MYSQL_DATABASE','MYSQL_PWD')) {
  $environment[$key] = [Environment]::GetEnvironmentVariable($key)
}
function Invoke-Docker([string[]]$Arguments) {
  $output = & docker @Arguments 2>&1
  if ($LASTEXITCODE -ne 0) { throw "Docker $($Arguments[0]) failed (exit=$LASTEXITCODE): $($output -join "`n")" }
  return ($output -join "`n").Trim()
}
function Sql([string]$Container, [string]$Query) {
  Invoke-Docker @('exec','--env','MYSQL_PWD',$Container,'mysql','-uroot','--batch','--raw',
    '--skip-column-names','--default-character-set=utf8mb4',$database,'-e',$Query)
}
function Start-Database([string]$Role, [string]$Image) {
  $name = "$batch-$Role"
  $volume = "$name-data"
  if ((Invoke-Docker @('volume','ls','--filter',"name=^${volume}$",'--format','{{.Name}}'))) {
    throw "Refusing existing volume: $volume"
  }
  Invoke-Docker @('volume','create','--label',"cgc-pms.mainline100=$batch",$volume) | Out-Null
  $volumes.Add($volume)
  # Register before run: the daemon may create the container but fail to start it.
  $containers.Add($name)
  Invoke-Docker @('run','-d','--name',$name,'--memory','512m','--label',"cgc-pms.mainline100=$batch",'--network',$network,
    '--env','MYSQL_ROOT_PASSWORD','--env','MYSQL_ROOT_HOST','--env','MYSQL_DATABASE',
    '--mount',"type=volume,source=$volume,target=/var/lib/mysql",
    '--mount',"type=bind,source=$RepoRoot/backend/src/main/resources/db/migration,target=/migrations,readonly",
    $Image,'--character-set-server=utf8mb4','--collation-server=utf8mb4_0900_ai_ci','--default-time-zone=+08:00') | Out-Null
  $ready = $false
  foreach ($attempt in 1..120) {
    & docker exec --env MYSQL_PWD $name mysql -uroot --host=127.0.0.1 --batch --skip-column-names -e 'SELECT 1' *> $null
    if ($LASTEXITCODE -eq 0) { $ready = $true; break }
    Start-Sleep -Seconds 1
  }
  if (!$ready) { throw "MySQL did not become ready: $name" }
  return $name
}
function Snapshot([string]$Container, [string]$Label, [string]$Source) {
  $tables = (Sql $Container "SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE' ORDER BY table_name") -split "`n"
  if ($tables.Count -lt 200) { throw 'Repository schema was not fully prepared' }
  $counts = foreach ($table in $tables) {
    if ($table -cnotmatch '^[a-z0-9_]+$') { throw 'Unexpected table identifier' }
    "SELECT '$table',COUNT(*) FROM $table"
  }
  $inventory = Sql $Container ($counts -join ' UNION ALL ')
  $objects = Sql $Container @'
SELECT 'TABLE',table_name,table_type,COALESCE(engine,''),COALESCE(table_collation,'') FROM information_schema.tables WHERE table_schema=DATABASE() ORDER BY table_name;
SELECT 'COLUMN',table_name,column_name,column_type,is_nullable,COALESCE(column_default,''),extra,COALESCE(collation_name,''),generation_expression FROM information_schema.columns WHERE table_schema=DATABASE() ORDER BY table_name,ordinal_position;
SELECT 'INDEX',table_name,index_name,seq_in_index,column_name,non_unique,COALESCE(sub_part,0) FROM information_schema.statistics WHERE table_schema=DATABASE() ORDER BY table_name,index_name,seq_in_index;
SELECT 'CONSTRAINT',table_name,constraint_name,constraint_type,enforced FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() ORDER BY table_name,constraint_name;
SELECT 'CHECK',constraint_name,check_clause FROM information_schema.check_constraints WHERE constraint_schema=DATABASE() ORDER BY constraint_name;
SELECT 'FK_ACTION',table_name,constraint_name,unique_constraint_name,match_option,update_rule,delete_rule FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() ORDER BY table_name,constraint_name;
SELECT 'FK',table_name,constraint_name,column_name,referenced_table_name,referenced_column_name FROM information_schema.key_column_usage WHERE constraint_schema=DATABASE() AND referenced_table_name IS NOT NULL ORDER BY table_name,constraint_name,ordinal_position;
SELECT 'ROUTINE',routine_name,routine_type,routine_definition FROM information_schema.routines WHERE routine_schema=DATABASE() ORDER BY routine_name;
SELECT 'TRIGGER',trigger_name,action_timing,event_manipulation,event_object_table,action_statement FROM information_schema.triggers WHERE trigger_schema=DATABASE() ORDER BY trigger_name;
SELECT 'VIEW',table_name,view_definition,security_type FROM information_schema.views WHERE table_schema=DATABASE() ORDER BY table_name;
'@
  # Use the SAME 8.0 dump client for canonical row serialization on every server.
  # The pre-upgrade backup itself is generated separately and never overwritten.
  $dumpCommand = 'exec mysqldump -uroot --host="$1" --single-transaction --no-create-info --skip-triggers --skip-comments --compact --skip-extended-insert --order-by-primary --hex-blob --set-gtid-purged=OFF --default-character-set=utf8mb4 "$2" > /tmp/m100-rows.sql'
  Invoke-Docker @('exec','--env','MYSQL_PWD',$Source,'sh','-c',$dumpCommand,'sh',$Container,$database) | Out-Null
  $rows = Join-Path $artifacts "$Label-rows.sql"
  Invoke-Docker @('cp',"${Source}:/tmp/m100-rows.sql",$rows) | Out-Null
  [IO.File]::WriteAllText((Join-Path $artifacts "$Label-counts.tsv"),$inventory)
  [IO.File]::WriteAllText((Join-Path $artifacts "$Label-objects.tsv"),$objects)
  return [pscustomobject]@{ tableCount=$tables.Count; counts=$inventory; objects=$objects; rowsSha256=(Get-FileHash $rows).Hash }
}
try {
  Get-Command docker -ErrorAction Stop | Out-Null
  if (!$TargetImage) { $TargetImage = & (Join-Path $PSScriptRoot 'build-mysql-runtime.ps1') -RepoRoot $RepoRoot }
  $TargetImage = Invoke-Docker @('image','inspect','--format','{{.Id}}',$TargetImage)
  if ($TargetImage -cnotmatch '^sha256:[0-9a-f]{64}$') { throw 'Target must resolve to an immutable image ID' }
  New-Item -ItemType Directory -Path $artifacts | Out-Null
  $env:MYSQL_ROOT_PASSWORD = 'm100-' + [guid]::NewGuid().ToString('N')
  $env:MYSQL_PWD = $env:MYSQL_ROOT_PASSWORD
  $env:MYSQL_ROOT_HOST = '%'
  $env:MYSQL_DATABASE = $database
  Invoke-Docker @('network','create','--internal','--label',"cgc-pms.mainline100=$batch",$network) | Out-Null
  $networkCreated = $true
  $source = Start-Database 'source80' $sourceImage
  $sourceVersion = Sql $source 'SELECT VERSION()'
  if ($sourceVersion -ne '8.0.46') { throw "Unexpected source: $sourceVersion" }
  # Prepare the actual current schema using the checked-in SQL and native 8.0
  # client, not an unsupported Connector/J 26.7 -> 8.0 connection. Flyway fresh
  # and V180 upgrades are separate required CI tests on the new server.
  $migrationRoot = Join-Path $RepoRoot 'backend/src/main/resources/db/migration'
  $schemaFiles = @('B215__cgc_pms_baseline.sql') + @(
    Get-ChildItem $migrationRoot -Filter 'V*.sql' | Sort-Object { [int]([regex]::Match($_.Name,'^V(\d+)').Groups[1].Value) } | Select-Object -ExpandProperty Name
  )
  foreach ($file in $schemaFiles) {
    Invoke-Docker @('exec','--env','MYSQL_PWD',$source,'sh','-c',
      'exec mysql -uroot --default-character-set=utf8mb4 "$1" < "/migrations/$2"','sh',$database,$file) | Out-Null
  }
  Invoke-Docker @('cp',(Join-Path $PSScriptRoot 'fixtures/mysql-engine-upgrade.sql'),"${source}:/tmp/m100-fixture.sql") | Out-Null
  Invoke-Docker @('exec','--env','MYSQL_PWD',$source,'sh','-c','exec mysql -uroot --default-character-set=utf8mb4 "$1" < /tmp/m100-fixture.sql','sh',$database) | Out-Null
  $before = Snapshot $source 'source80' $source
  Invoke-Docker @('cp',"${source}:/etc/my.cnf",(Join-Path $artifacts 'source-my.cnf')) | Out-Null
  $checkerCode = @'
import os
util.check_for_server_upgrade({'host':os.environ['M100_SOURCE'],'user':'root','password':os.environ['MYSQL_PWD']}, {'targetVersion':'8.4.12','outputFormat':'JSON','configPath':'/evidence/source-my.cnf'})
'@
  $previousSource = $env:M100_SOURCE
  try {
    $env:M100_SOURCE = $source
    $checkerOutput = Invoke-Docker @('run','--rm','--network',$network,'--env','MYSQL_PWD','--env','M100_SOURCE',
      '--mount',"type=bind,source=$artifacts,target=/evidence,readonly",'--entrypoint','mysqlsh',$checkerImage,'--py','--quiet-start=2','-e',$checkerCode)
  }
  finally { $env:M100_SOURCE = $previousSource }
  [IO.File]::WriteAllText((Join-Path $artifacts 'upgrade-checker.json'),$checkerOutput)
  $jsonStart = $checkerOutput.IndexOf('{')
  if ($jsonStart -lt 0) { throw 'Upgrade Checker did not return JSON' }
  $checker = $checkerOutput.Substring($jsonStart) | ConvertFrom-Json
  if ($checker.targetVersion -ne '8.4.12' -or $null -eq $checker.errorCount -or $checker.errorCount -ne 0 -or
      @($checker.checksPerformed | Where-Object status -ne 'OK').Count -gt 0) { throw 'Upgrade Checker did not pass; target not started' }
  # Reviewed defaults: no replication/cutover; InnoDB defaults are exercised by
  # required MySQL concurrency tests. System accounts are recreated, never dumped.
  $reviewedDefaults = @('binlog_transaction_dependency_tracking','group_replication_consistency',
    'group_replication_exit_state_action','innodb_adaptive_hash_index','innodb_buffer_pool_in_core_file',
    'innodb_buffer_pool_instances','innodb_change_buffering','innodb_doublewrite_files','innodb_doublewrite_pages',
    'innodb_flush_method','innodb_io_capacity','innodb_io_capacity_max','innodb_log_buffer_size',
    'innodb_numa_interleave','innodb_page_cleaners','innodb_parallel_read_threads','innodb_purge_threads',
    'innodb_read_io_threads','innodb_redo_log_capacity','innodb_use_fdatasync','performance_schema_max_memory_classes',
    'temptable_max_mmap','temptable_max_ram','temptable_use_mmap')
  foreach ($check in $checker.checksPerformed) {
    foreach ($problem in $check.detectedProblems) {
      if ($check.id -eq 'sysVars' -and $problem.level -eq 'Warning' -and
          $problem.dbObject -in $reviewedDefaults -and $problem.description.StartsWith('default value changed from ')) { continue }
      if ($check.id -eq 'invalidPrivileges' -and $problem.level -eq 'Notice' -and
          $problem.dbObject -in @("'root'@'%'","'root'@'localhost'") -and $problem.description.EndsWith('SET_USER_ID')) { continue }
      throw "Unreviewed Upgrade Checker finding: $($check.id)/$($problem.dbObject)"
    }
  }
  if (@($checker.manualChecks | Where-Object { $_ }).Count -gt 0) { throw 'Upgrade Checker requires unreviewed manual checks' }
  Write-Host "Upgrade Checker PASS: errors=$($checker.errorCount), warnings=$($checker.warningCount), notices=$($checker.noticeCount)"
  Invoke-Docker @('exec','--env','MYSQL_PWD',$source,'sh','-c',
    'exec mysqldump -uroot --single-transaction --routines --triggers --events --hex-blob --set-gtid-purged=OFF --default-character-set=utf8mb4 "$1" > /tmp/m100-preupgrade.sql','sh',$database) | Out-Null
  $backup = Join-Path $artifacts 'preupgrade80.sql'
  Invoke-Docker @('cp',"${source}:/tmp/m100-preupgrade.sql",$backup) | Out-Null
  $backupHash = (Get-FileHash $backup).Hash
  # First prove rollback with this exact pre-upgrade 8.0 dump, then import it to 8.4.
  foreach ($destination in @(@{role='restore80';image=$sourceImage;version='8.0.46'},@{role='target84';image=$TargetImage;version='8.4.12'})) {
    if ((Get-FileHash $backup).Hash -ne $backupHash) { throw 'Pre-upgrade backup changed' }
    $target = Start-Database $destination.role $destination.image
    if ((Sql $target 'SELECT VERSION()') -ne $destination.version) { throw 'Destination version mismatch' }
    Invoke-Docker @('cp',$backup,"${target}:/tmp/m100-preupgrade.sql") | Out-Null
    Invoke-Docker @('exec','--env','MYSQL_PWD',$target,'sh','-c',
      'exec mysql -uroot --default-character-set=utf8mb4 "$1" < /tmp/m100-preupgrade.sql','sh',$database) | Out-Null
    $after = Snapshot $target $destination.role $source
    if ($before.counts -cne $after.counts -or $before.objects -cne $after.objects -or $before.rowsSha256 -ne $after.rowsSha256) {
      throw "Schema/row/byte invariant mismatch for $($destination.role); inspect retained evidence"
    }
    if ((Sql $target 'CALL m100_engine_probe()') -ne "0001FF0D0A7F80`nFEEDBEEF") { throw 'Routine/binary probe mismatch' }
    Write-Host "$($destination.role) PASS: $($after.tableCount) table counts, all row bytes and object definitions unchanged"
    # Retain the recovery volume, not an idle server. Keep at most source + one
    # destination alive on constrained local Docker hosts.
    Invoke-Docker @('stop','--timeout','15',$target) | Out-Null
  }
  $evidence = [ordered]@{ok=$true;batch=$batch;sourceVersion=$sourceVersion;targetVersion='8.4.12';
    sourceImage=$sourceImage;targetImage=$TargetImage;checkerImage=$checkerImage;
    dumpClient=(Invoke-Docker @('exec',$source,'mysqldump','--version'));preupgradeSha256=$backupHash;
    tableCount=$before.tableCount;allRowsSha256=$before.rowsSha256;rollback80Verified=$true;target84Verified=$true;
    existingDevVolumeTouched=$false;resourcesRetained=[bool]$KeepResources}
  [IO.File]::WriteAllText((Join-Path $artifacts 'result.json'),($evidence | ConvertTo-Json -Depth 5))
  Invoke-Docker @('stop','--timeout','15',$source) | Out-Null
  $evidence | ConvertTo-Json -Depth 5
}
finally {
 try {
  # Local M6 keeps source + dump until G5. CI may remove only this unique labeled batch.
  [IO.Directory]::CreateDirectory($artifacts) | Out-Null
  [IO.File]::WriteAllText((Join-Path $artifacts 'resources.json'),(@{batch=$batch;containers=@($containers);volumes=@($volumes);network=$network;retained=[bool]$KeepResources} | ConvertTo-Json -Depth 4))
  if (!$KeepResources) {
    $cleanupErrors = [Collections.Generic.List[string]]::new()
    foreach ($container in $containers) {
     try {
      $existing = Invoke-Docker @('ps','-a','--filter',"name=^/${container}$",'--format','{{.Names}}')
      if (!$existing) { continue }
      if ($existing -cne $container) { throw 'Unexpected container lookup result' }
      $owner = Invoke-Docker @('inspect','--format','{{index .Config.Labels "cgc-pms.mainline100"}}',$container)
      if ($owner -ne $batch) { throw 'Container ownership mismatch; refusing cleanup' }
      Invoke-Docker @('rm','-f',$container) | Out-Null
     } catch { $cleanupErrors.Add("${container}: $($_.Exception.Message)") }
    }
    foreach ($volume in $volumes) {
     try {
      $owner = Invoke-Docker @('volume','inspect','--format','{{index .Labels "cgc-pms.mainline100"}}',$volume)
      if ($owner -ne $batch) { throw 'Volume ownership mismatch; refusing cleanup' }
      Invoke-Docker @('volume','rm',$volume) | Out-Null
     } catch { $cleanupErrors.Add("${volume}: $($_.Exception.Message)") }
    }
    if ($networkCreated) {
     try {
      $owner = Invoke-Docker @('network','inspect','--format','{{index .Labels "cgc-pms.mainline100"}}',$network)
      if ($owner -ne $batch) { throw 'Network ownership mismatch; refusing cleanup' }
      Invoke-Docker @('network','rm',$network) | Out-Null
     } catch { $cleanupErrors.Add("${network}: $($_.Exception.Message)") }
    }
    if ($cleanupErrors.Count) { throw "Batch cleanup incomplete: $($cleanupErrors -join '; ')" }
  }
 } finally {
  foreach ($key in $environment.Keys) {
    if ($null -eq $environment[$key]) { Remove-Item -LiteralPath "Env:$key" -ErrorAction SilentlyContinue }
    else { [Environment]::SetEnvironmentVariable($key,$environment[$key]) }
  }
 }
}
