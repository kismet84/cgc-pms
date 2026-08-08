#define UNICODE
#define _UNICODE
#include <windows.h>
#include <filesystem>
#include <fstream>
#include <string>
#include <vector>

namespace fs = std::filesystem;

std::string Utf8(const std::wstring& value) {
  int size = WideCharToMultiByte(CP_UTF8, WC_ERR_INVALID_CHARS, value.data(),
                                 static_cast<int>(value.size()), nullptr, 0, nullptr, nullptr);
  if (size <= 0) return {};
  std::string result(size, '\0');
  WideCharToMultiByte(CP_UTF8, WC_ERR_INVALID_CHARS, value.data(), static_cast<int>(value.size()),
                      result.data(), size, nullptr, nullptr);
  return result;
}

int wmain(int argc, wchar_t** argv) {
  std::vector<wchar_t> buffer(32768);
  DWORD length = GetEnvironmentVariableW(L"LOCALAPPDATA", buffer.data(), static_cast<DWORD>(buffer.size()));
  if (!length || length >= buffer.size()) return 90;
  fs::path directory = fs::path(std::wstring(buffer.data(), length)) / L"CGC-PMS" / L"Desktop" / L"contract-evidence";
  fs::create_directories(directory);
  std::ofstream args(directory / L"fake-argv.txt", std::ios::trunc | std::ios::binary);
  for (int i = 0; i < argc; ++i) args << Utf8(argv[i]) << '\n';
  args.close();
  std::ofstream pid(directory / L"fake-pid.txt", std::ios::trunc);
  pid << GetCurrentProcessId() << '\n';
  pid.close();
  if (fs::exists(directory / L"hold.flag")) Sleep(8000);
  int code = 0;
  std::ifstream exitCode(directory / L"exit-code.txt");
  if (exitCode) exitCode >> code;
  return code;
}
