#define UNICODE
#define _UNICODE
#include <windows.h>
#include <fstream>
#include <string>
#include <vector>

std::wstring Quote(const std::wstring& value) {
  std::wstring result = L"\"";
  size_t slashes = 0;
  for (wchar_t ch : value) {
    if (ch == L'\\') { ++slashes; continue; }
    if (ch == L'\"') {
      result.append(slashes * 2 + 1, L'\\');
      result.push_back(ch);
      slashes = 0;
      continue;
    }
    result.append(slashes, L'\\');
    slashes = 0;
    result.push_back(ch);
  }
  result.append(slashes * 2, L'\\');
  result.push_back(L'\"');
  return result;
}

std::wstring Extended(const std::wstring& value) {
  if (value.rfind(L"\\\\?\\", 0) == 0) return value;
  if (value.rfind(L"\\\\", 0) == 0) return L"\\\\?\\UNC\\" + value.substr(2);
  return L"\\\\?\\" + value;
}

int wmain(int argc, wchar_t** argv) {
  if (argc < 2) return 250;
  const std::wstring target = Extended(argv[1]);
  std::wstring command = Quote(target);
  for (int index = 2; index < argc; ++index) command += L" " + Quote(argv[index]);
  std::vector<wchar_t> mutableCommand(command.begin(), command.end());
  mutableCommand.push_back(L'\0');
  STARTUPINFOW startup{};
  startup.cb = sizeof(startup);
  PROCESS_INFORMATION process{};
  if (!CreateProcessW(target.c_str(), mutableCommand.data(), nullptr, nullptr, FALSE, 0,
                      nullptr, nullptr, &startup, &process)) return 251;
  CloseHandle(process.hThread);
  wchar_t pidFile[32768]{};
  if (GetEnvironmentVariableW(L"CGCPMS_PROCESS_HARNESS_PID_FILE", pidFile, 32768) > 0) {
    std::wofstream output(pidFile, std::ios::trunc);
    output << process.dwProcessId;
  }
  WaitForSingleObject(process.hProcess, INFINITE);
  DWORD exitCode = 252;
  GetExitCodeProcess(process.hProcess, &exitCode);
  CloseHandle(process.hProcess);
  return static_cast<int>(exitCode);
}
