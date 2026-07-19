const forbiddenRules = [
  {
    id: 'legacy-source-path',
    pattern: /(?:^|["'`(])(?:\.\.\/)+(?:frontend-admin)(?:\/|$)|frontend-admin\/src\//g,
    message: '禁止导入 frontend-admin 源码。',
  },
  {
    id: 'legacy-alias',
    pattern: /["'`]@legacy(?:\/|["'`])/g,
    message: '禁止使用 Legacy 别名。',
  },
  {
    id: 'legacy-style',
    pattern: /frontend-admin[^\n"'`]*\.(?:css|less|scss|sass)["'`]/g,
    message: '禁止导入 Legacy 样式。',
  },
]

export function findBoundaryViolations(source, file = '<inline>') {
  return forbiddenRules.flatMap((rule) => {
    rule.pattern.lastIndex = 0
    const matches = [...source.matchAll(rule.pattern)]
    return matches.map((match) => ({
      file,
      rule: rule.id,
      message: rule.message,
      match: match[0],
    }))
  })
}

const contractUiRules = [
  {
    id: 'contract-ui-dependency',
    pattern: /from\s+["'](?:vue|pinia|vue-router|@vue\/[^"']+)["']/g,
    message: 'frontend-contracts 禁止依赖 UI 或状态框架。',
  },
  {
    id: 'contract-browser-state',
    pattern: /\b(?:window|document|localStorage|sessionStorage)\b/g,
    message: 'frontend-contracts 禁止访问浏览器状态。',
  },
  {
    id: 'contract-style-import',
    pattern: /import\s+["'][^"']+\.(?:css|less|scss|sass)["']/g,
    message: 'frontend-contracts 禁止导入样式。',
  },
]

export function findContractViolations(source, file = '<inline>') {
  return contractUiRules.flatMap((rule) => {
    rule.pattern.lastIndex = 0
    return [...source.matchAll(rule.pattern)].map((match) => ({
      file,
      rule: rule.id,
      message: rule.message,
      match: match[0],
    }))
  })
}
