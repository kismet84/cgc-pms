#define UNICODE
#define _UNICODE
#include <windows.h>
#include <winhttp.h>
#include <shlobj.h>
#include <cctype>
#include <filesystem>
#include <fstream>
#include <optional>
#include <sstream>
#include <string>
#include <vector>

#pragma comment(lib, "winhttp.lib")
#pragma comment(lib, "shell32.lib")
#pragma comment(lib, "ole32.lib")
#pragma comment(lib, "user32.lib")
#pragma comment(lib, "version.lib")

namespace fs = std::filesystem;

#ifndef CGCPMS_HEALTH_PORT
#define CGCPMS_HEALTH_PORT 5173
#endif
#ifndef CGCPMS_HEALTH_ATTEMPTS
#define CGCPMS_HEALTH_ATTEMPTS 30
#endif
#ifndef CGCPMS_HEALTH_DELAY_MS
#define CGCPMS_HEALTH_DELAY_MS 1000
#endif

constexpr wchar_t kAppUrl[] = L"http://127.0.0.1:5173/?desktop=1";
constexpr wchar_t kHealthPath[] = L"/api/actuator/health";
constexpr int kWindowWidth = 1440;
constexpr int kWindowHeight = 1080;
constexpr DWORD kWindowWaitMs = 10000;
constexpr DWORD kWindowStabilizeMs = 750;
constexpr DWORD kWindowMaintainMs = 250;
#ifdef CGCPMS_CONTRACT_TEST
constexpr wchar_t kWindowConfiguredEvent[] = L"Local\\CGCPMS.Desktop.WindowConfigured.Contract";
constexpr wchar_t kMutexName[] = L"Local\\CGCPMS.Desktop.Launcher.Contract";
constexpr wchar_t kRuntimeDir[] = L"contract-runtime";
constexpr wchar_t kProfileDir[] = L"contract-profiles";
constexpr wchar_t kLogDir[] = L"contract-logs";
#else
constexpr wchar_t kMutexName[] = L"Local\\CGCPMS.Desktop.Launcher";
constexpr wchar_t kRuntimeDir[] = L"runtime";
constexpr wchar_t kProfileDir[] = L"profiles";
constexpr wchar_t kLogDir[] = L"logs";
#endif

#ifdef CGCPMS_CONTRACT_TEST
std::wstring ContractObjectName(const wchar_t* base) {
  wchar_t runId[64]{};
  const DWORD length = GetEnvironmentVariableW(L"CGCPMS_CONTRACT_RUN_ID", runId, 64);
  if (length != 32) return {};
  for (DWORD index = 0; index < length; ++index) {
    if (!((runId[index] >= L'0' && runId[index] <= L'9') ||
          (runId[index] >= L'a' && runId[index] <= L'f'))) return {};
  }
  return std::wstring(base) + L"." + std::wstring(runId, length);
}
#endif

enum ExitCode {
  kOk = 0,
  kInvalidInvocation = 10,
  kAlreadyRunning = 20,
  kOrphanBrowserRunning = 21,
  kRuntimeMissing = 30,
  kHealthFailed = 31,
  kLaunchFailed = 32,
  kUnsafeRuntimePath = 33,
  kBrowserFailed = 40,
};

struct UniqueHandle {
  HANDLE value = nullptr;
  UniqueHandle() = default;
  explicit UniqueHandle(HANDLE h) : value(h) {}
  ~UniqueHandle() { if (value && value != INVALID_HANDLE_VALUE) CloseHandle(value); }
  UniqueHandle(const UniqueHandle&) = delete;
  UniqueHandle& operator=(const UniqueHandle&) = delete;
  explicit operator bool() const { return value && value != INVALID_HANDLE_VALUE; }
};

struct UniqueInternetHandle {
  HINTERNET value = nullptr;
  UniqueInternetHandle() = default;
  explicit UniqueInternetHandle(HINTERNET handle) : value(handle) {}
  ~UniqueInternetHandle() { if (value) WinHttpCloseHandle(value); }
  UniqueInternetHandle(const UniqueInternetHandle&) = delete;
  UniqueInternetHandle& operator=(const UniqueInternetHandle&) = delete;
  explicit operator bool() const { return value != nullptr; }
};

std::wstring ModuleDirectory() {
  std::vector<wchar_t> buffer(32768);
  DWORD length = GetModuleFileNameW(nullptr, buffer.data(), static_cast<DWORD>(buffer.size()));
  if (!length || length >= buffer.size()) return {};
  return fs::path(std::wstring(buffer.data(), length)).parent_path().wstring();
}

std::wstring LocalDataRoot() {
#ifdef CGCPMS_CONTRACT_TEST
  wchar_t raw[32768]{};
  const DWORD length = GetEnvironmentVariableW(L"CGCPMS_CONTRACT_DATA_ROOT", raw, 32768);
  if (!length || length >= 32768) return {};
  const fs::path path(std::wstring(raw, length));
  std::error_code ec;
  if (!path.is_absolute() || path.lexically_normal() != path || !fs::is_directory(path, ec) || ec) return {};
  return path.wstring();
#else
  PWSTR raw = nullptr;
  if (FAILED(SHGetKnownFolderPath(FOLDERID_LocalAppData, KF_FLAG_CREATE, nullptr, &raw))) return {};
  fs::path path(raw);
  CoTaskMemFree(raw);
  return (path / L"CGC-PMS" / L"Desktop").wstring();
#endif
}

std::string Utf8(const std::wstring& value) {
  if (value.empty()) return {};
  int size = WideCharToMultiByte(CP_UTF8, WC_ERR_INVALID_CHARS, value.data(),
                                 static_cast<int>(value.size()), nullptr, 0, nullptr, nullptr);
  if (size <= 0) return {};
  std::string result(size, '\0');
  WideCharToMultiByte(CP_UTF8, WC_ERR_INVALID_CHARS, value.data(),
                      static_cast<int>(value.size()), result.data(), size, nullptr, nullptr);
  return result;
}

void RotateLogs(const fs::path& directory) {
  std::error_code ec;
  fs::create_directories(directory, ec);
  const fs::path current = directory / L"launcher.log";
  if (!fs::exists(current, ec) || fs::file_size(current, ec) < 1024 * 1024) return;
  fs::remove(directory / L"launcher.log.4", ec);
  for (int i = 3; i >= 1; --i) {
    fs::path from = directory / (L"launcher.log." + std::to_wstring(i));
    fs::path to = directory / (L"launcher.log." + std::to_wstring(i + 1));
    if (fs::exists(from, ec)) fs::rename(from, to, ec);
  }
  fs::rename(current, directory / L"launcher.log.1", ec);
}

void Log(const fs::path& root, const std::string& stage, const std::string& result,
         DWORD code = 0, DWORD pid = 0) {
  try {
    const fs::path directory = root / kLogDir;
    RotateLogs(directory);
    SYSTEMTIME now{};
    GetSystemTime(&now);
    std::ofstream out(directory / L"launcher.log", std::ios::app | std::ios::binary);
    if (!out) return;
    out << now.wYear << '-' << now.wMonth << '-' << now.wDay << 'T'
        << now.wHour << ':' << now.wMinute << ':' << now.wSecond << 'Z'
        << " stage=" << stage << " result=" << result
        << " code=" << code << " pid=" << pid << "\n";
  } catch (...) {
    // Logging must never block launcher behavior.
  }
}

void Inform(const wchar_t* text, UINT flags = MB_OK | MB_ICONINFORMATION) {
#ifdef CGCPMS_CONTRACT_TEST
  (void)flags;
  DWORD written = 0;
  WriteConsoleW(GetStdHandle(STD_ERROR_HANDLE), text, static_cast<DWORD>(wcslen(text)), &written, nullptr);
  WriteConsoleW(GetStdHandle(STD_ERROR_HANDLE), L"\n", 1, &written, nullptr);
#else
  MessageBoxW(nullptr, text, L"CGC-PMS", flags | MB_SETFOREGROUND);
#endif
}

bool HasReparsePoint(const fs::path& path) {
  DWORD attrs = GetFileAttributesW(path.c_str());
  return attrs != INVALID_FILE_ATTRIBUTES && (attrs & FILE_ATTRIBUTE_REPARSE_POINT) != 0;
}

bool RuntimePathIsSafe(const fs::path& module, const fs::path& chromium, const fs::path& executable) {
  if (HasReparsePoint(chromium) || HasReparsePoint(executable)) return false;
  std::error_code ec;
  fs::path normalizedModule = fs::weakly_canonical(module, ec);
  if (ec) return false;
  fs::path normalizedExecutable = fs::weakly_canonical(executable, ec);
  if (ec) return false;
  auto parentText = normalizedModule.native();
  auto executableText = normalizedExecutable.native();
  if (!parentText.empty() && parentText.back() != L'\\') parentText.push_back(L'\\');
  return executableText.size() > parentText.size() &&
         _wcsnicmp(executableText.c_str(), parentText.c_str(), parentText.size()) == 0;
}

std::wstring QuoteArgument(const std::wstring& value) {
  if (value.find_first_of(L" \t\"") == std::wstring::npos) return value;
  std::wstring result = L"\"";
  size_t slashes = 0;
  for (wchar_t ch : value) {
    if (ch == L'\\') {
      ++slashes;
    } else if (ch == L'\"') {
      result.append(slashes * 2 + 1, L'\\');
      result.push_back(L'\"');
      slashes = 0;
    } else {
      result.append(slashes, L'\\');
      slashes = 0;
      result.push_back(ch);
    }
  }
  result.append(slashes * 2, L'\\');
  result.push_back(L'\"');
  return result;
}

std::wstring ExtendedWin32Path(const fs::path& value) {
  std::wstring absolute = fs::absolute(value).wstring();
  if (absolute.rfind(L"\\\\?\\", 0) == 0) return absolute;
  if (absolute.rfind(L"\\\\", 0) == 0) return L"\\\\?\\UNC\\" + absolute.substr(2);
  return L"\\\\?\\" + absolute;
}

int ChromiumMajor(const fs::path& executable) {
  DWORD ignored = 0;
  DWORD size = GetFileVersionInfoSizeW(executable.c_str(), &ignored);
  if (!size) return 0;
  std::vector<BYTE> data(size);
  if (!GetFileVersionInfoW(executable.c_str(), 0, size, data.data())) return 0;
  VS_FIXEDFILEINFO* info = nullptr;
  UINT length = 0;
  if (!VerQueryValueW(data.data(), L"\\", reinterpret_cast<void**>(&info), &length) || !info) return 0;
  return static_cast<int>(HIWORD(info->dwFileVersionMS));
}

bool IsTopLevelStatusUp(const std::string& body) {
  size_t i = 0;
  auto skip = [&] { while (i < body.size() && isspace(static_cast<unsigned char>(body[i]))) ++i; };
  skip();
  if (i >= body.size() || body[i++] != '{') return false;
  int depth = 1;
  while (i < body.size() && depth == 1) {
    skip();
    if (i < body.size() && body[i] == '}') return false;
    if (i >= body.size() || body[i++] != '"') return false;
    std::string key;
    bool escaped = false;
    while (i < body.size()) {
      char ch = body[i++];
      if (escaped) { key.push_back(ch); escaped = false; continue; }
      if (ch == '\\') { escaped = true; continue; }
      if (ch == '"') break;
      key.push_back(ch);
    }
    skip();
    if (i >= body.size() || body[i++] != ':') return false;
    skip();
    if (key == "status") {
      if (i >= body.size() || body[i++] != '"') return false;
      std::string value;
      while (i < body.size() && body[i] != '"') value.push_back(body[i++]);
      return i < body.size() && body[i] == '"' && value == "UP";
    }
    bool inString = false;
    escaped = false;
    int nested = 0;
    while (i < body.size()) {
      char ch = body[i++];
      if (inString) {
        if (escaped) escaped = false;
        else if (ch == '\\') escaped = true;
        else if (ch == '"') inString = false;
        continue;
      }
      if (ch == '"') inString = true;
      else if (ch == '{' || ch == '[') ++nested;
      else if (ch == '}' || ch == ']') {
        if (nested == 0) { --i; break; }
        --nested;
      } else if (ch == ',' && nested == 0) break;
    }
    skip();
  }
  return false;
}

bool HealthUp() {
  UniqueInternetHandle session(WinHttpOpen(L"CGC-PMS-Desktop/1.0", WINHTTP_ACCESS_TYPE_NO_PROXY,
                                           WINHTTP_NO_PROXY_NAME, WINHTTP_NO_PROXY_BYPASS, 0));
  if (!session) return false;
  WinHttpSetTimeouts(session.value, 2000, 2000, 2000, 2000);
  UniqueInternetHandle connection(WinHttpConnect(session.value, L"127.0.0.1", CGCPMS_HEALTH_PORT, 0));
  if (!connection) return false;
  UniqueInternetHandle request(WinHttpOpenRequest(connection.value, L"GET", kHealthPath, nullptr,
                                                  WINHTTP_NO_REFERER, WINHTTP_DEFAULT_ACCEPT_TYPES, 0));
  if (!request) return false;
  if (!WinHttpSendRequest(request.value, WINHTTP_NO_ADDITIONAL_HEADERS, 0,
                          WINHTTP_NO_REQUEST_DATA, 0, 0, 0) ||
      !WinHttpReceiveResponse(request.value, nullptr)) return false;
  DWORD status = 0;
  DWORD statusSize = sizeof(status);
  if (!WinHttpQueryHeaders(request.value, WINHTTP_QUERY_STATUS_CODE | WINHTTP_QUERY_FLAG_NUMBER,
                           WINHTTP_HEADER_NAME_BY_INDEX, &status, &statusSize, WINHTTP_NO_HEADER_INDEX) ||
      status < 200 || status >= 300) return false;
  std::string body;
  for (;;) {
    DWORD available = 0;
    if (!WinHttpQueryDataAvailable(request.value, &available)) return false;
    if (!available) break;
    if (body.size() + available > 8192) return false;
    size_t offset = body.size();
    body.resize(offset + available);
    DWORD read = 0;
    if (!WinHttpReadData(request.value, body.data() + offset, available, &read)) return false;
    body.resize(offset + read);
  }
  return IsTopLevelStatusUp(body);
}

bool WaitForHealth() {
  for (int attempt = 0; attempt < CGCPMS_HEALTH_ATTEMPTS; ++attempt) {
    if (HealthUp()) return true;
    if (attempt + 1 < CGCPMS_HEALTH_ATTEMPTS) Sleep(CGCPMS_HEALTH_DELAY_MS);
  }
#ifdef CGCPMS_CONTRACT_TEST
  return false;
#else
  if (MessageBoxW(nullptr,
                  L"本地 CGC-PMS 服务未就绪。请先运行 scripts\\start-dev.bat。是否再检查一次？",
                  L"CGC-PMS", MB_RETRYCANCEL | MB_ICONWARNING | MB_SETFOREGROUND) != IDRETRY) return false;
  for (int attempt = 0; attempt < CGCPMS_HEALTH_ATTEMPTS; ++attempt) {
    if (HealthUp()) return true;
    if (attempt + 1 < CGCPMS_HEALTH_ATTEMPTS) Sleep(CGCPMS_HEALTH_DELAY_MS);
  }
  return false;
#endif
}

uint64_t FileTimeValue(const FILETIME& time) {
  ULARGE_INTEGER value{};
  value.LowPart = time.dwLowDateTime;
  value.HighPart = time.dwHighDateTime;
  return value.QuadPart;
}

std::optional<uint64_t> ParseNumber(const std::string& text, const std::string& key) {
  const std::string token = "\"" + key + "\":";
  size_t pos = text.find(token);
  if (pos == std::string::npos) return std::nullopt;
  pos += token.size();
  size_t end = pos;
  while (end < text.size() && isdigit(static_cast<unsigned char>(text[end]))) ++end;
  if (end == pos) return std::nullopt;
  try { return std::stoull(text.substr(pos, end - pos)); } catch (...) { return std::nullopt; }
}

bool ProcessMatches(DWORD pid, uint64_t creation) {
  UniqueHandle process(OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION | SYNCHRONIZE, FALSE, pid));
  if (!process) return false;
  if (WaitForSingleObject(process.value, 0) != WAIT_TIMEOUT) return false;
  FILETIME created{}, exited{}, kernel{}, user{};
  return GetProcessTimes(process.value, &created, &exited, &kernel, &user) && FileTimeValue(created) == creation;
}

bool ExistingBrowserAlive(const fs::path& statePath) {
  try {
    if (!fs::exists(statePath)) return false;
    std::ifstream input(statePath, std::ios::binary);
    std::ostringstream buffer;
    buffer << input.rdbuf();
    auto pid = ParseNumber(buffer.str(), "pid");
    auto created = ParseNumber(buffer.str(), "created");
    if (pid && created && *pid <= MAXDWORD && ProcessMatches(static_cast<DWORD>(*pid), *created)) return true;
    std::error_code ec;
    fs::remove(statePath, ec);
  } catch (...) {
    return true;
  }
  return false;
}

bool WriteState(const fs::path& statePath, DWORD pid, uint64_t created, int major) {
  try {
    fs::create_directories(statePath.parent_path());
    fs::path temp = statePath;
    temp += L".tmp";
    std::ofstream out(temp, std::ios::binary | std::ios::trunc);
    out << "{\"pid\":" << pid << ",\"created\":" << created << ",\"chromiumMajor\":" << major << "}\n";
    out.flush();
    if (!out) return false;
    out.close();
    return MoveFileExW(temp.c_str(), statePath.c_str(), MOVEFILE_REPLACE_EXISTING | MOVEFILE_WRITE_THROUGH) != FALSE;
  } catch (...) {
    return false;
  }
}

struct WindowSearch {
  DWORD pid = 0;
  HWND window = nullptr;
};

BOOL CALLBACK FindChromiumWindow(HWND window, LPARAM parameter) {
  auto* search = reinterpret_cast<WindowSearch*>(parameter);
  DWORD pid = 0;
  GetWindowThreadProcessId(window, &pid);
  if (pid != search->pid) return TRUE;
  wchar_t className[64]{};
  if (!GetClassNameW(window, className, static_cast<int>(std::size(className)))) return TRUE;
  if (wcscmp(className, L"Chrome_WidgetWin_1") != 0) return TRUE;
  const LONG_PTR style = GetWindowLongPtrW(window, GWL_STYLE);
  if (GetWindow(window, GW_OWNER) != nullptr || (style & WS_CHILD) != 0 ||
      (style & WS_POPUP) != 0 || (style & WS_CAPTION) != WS_CAPTION ||
      (style & WS_SYSMENU) == 0) {
    return TRUE;
  }
  search->window = window;
  return FALSE;
}

HWND WaitForChromiumWindow(DWORD pid, HANDLE process) {
  const ULONGLONG deadline = GetTickCount64() + kWindowWaitMs;
  do {
    WindowSearch search{pid, nullptr};
    EnumWindows(FindChromiumWindow, reinterpret_cast<LPARAM>(&search));
    if (search.window) return search.window;
    if (WaitForSingleObject(process, 50) != WAIT_TIMEOUT) return nullptr;
  } while (GetTickCount64() < deadline);
  return nullptr;
}

bool CompleteWindowConfiguration() {
#ifdef CGCPMS_CONTRACT_TEST
  const std::wstring eventName = ContractObjectName(kWindowConfiguredEvent);
  UniqueHandle configuredEvent(OpenEventW(EVENT_MODIFY_STATE, FALSE, eventName.c_str()));
  return configuredEvent && SetEvent(configuredEvent.value) != FALSE;
#else
  return true;
#endif
}

bool FailWindowConfiguration(const fs::path& dataRoot, DWORD pid, const char* result,
                             DWORD code = 0) {
  Log(dataRoot, "window", result, code, pid);
  return false;
}

bool ConfigureChromiumWindow(DWORD pid, HANDLE process, const fs::path& dataRoot) {
  HWND window = WaitForChromiumWindow(pid, process);
  if (!window) {
    DWORD exitCode = STILL_ACTIVE;
    GetExitCodeProcess(process, &exitCode);
    return FailWindowConfiguration(dataRoot, pid, "not_found", exitCode);
  }
  if (WaitForSingleObject(process, kWindowStabilizeMs) != WAIT_TIMEOUT) {
    DWORD exitCode = 0;
    GetExitCodeProcess(process, &exitCode);
    return FailWindowConfiguration(dataRoot, pid, "exited_during_stabilize", exitCode);
  }

  SetLastError(ERROR_SUCCESS);
  LONG_PTR style = GetWindowLongPtrW(window, GWL_STYLE);
  if (!style && GetLastError() != ERROR_SUCCESS) {
    return FailWindowConfiguration(dataRoot, pid, "style_read_failed", GetLastError());
  }
  const LONG_PTR fixedStyle = (style & ~static_cast<LONG_PTR>(WS_THICKFRAME)) |
                              WS_SYSMENU | WS_MINIMIZEBOX | WS_MAXIMIZEBOX;
  SetLastError(ERROR_SUCCESS);
  if (!SetWindowLongPtrW(window, GWL_STYLE, fixedStyle) && GetLastError() != ERROR_SUCCESS) {
    return FailWindowConfiguration(dataRoot, pid, "style_write_failed", GetLastError());
  }

  const UINT dpi = GetDpiForWindow(window);
  if (!dpi) return FailWindowConfiguration(dataRoot, pid, "dpi_failed", GetLastError());
  const int width = MulDiv(kWindowWidth, static_cast<int>(dpi), USER_DEFAULT_SCREEN_DPI);
  const int height = MulDiv(kWindowHeight, static_cast<int>(dpi), USER_DEFAULT_SCREEN_DPI);
  HMONITOR monitor = MonitorFromWindow(window, MONITOR_DEFAULTTONEAREST);
  MONITORINFO info{};
  info.cbSize = sizeof(info);
  if (!monitor || !GetMonitorInfoW(monitor, &info)) {
    return FailWindowConfiguration(dataRoot, pid, "monitor_failed", GetLastError());
  }
  const int availableWidth = info.rcWork.right - info.rcWork.left;
  const int availableHeight = info.rcWork.bottom - info.rcWork.top;
  const int left = availableWidth >= width
                       ? info.rcWork.left + (availableWidth - width) / 2
                       : info.rcWork.left;
  const int top = availableHeight >= height
                      ? info.rcWork.top + (availableHeight - height) / 2
                      : info.rcWork.top;

  if (availableWidth < width || availableHeight < height) {
    if (!SetWindowPos(window, nullptr, left, top, width, height,
                      SWP_NOZORDER | SWP_NOACTIVATE | SWP_FRAMECHANGED | SWP_SHOWWINDOW)) {
      return FailWindowConfiguration(dataRoot, pid, "maximize_position_failed", GetLastError());
    }
    ShowWindow(window, SW_MAXIMIZE);
    if (IsZoomed(window) == FALSE) {
      return FailWindowConfiguration(dataRoot, pid, "maximize_state_failed", GetLastError());
    }
  } else {
    ShowWindow(window, SW_RESTORE);
    const bool configured = SetWindowPos(
                                window, nullptr, left, top, width, height,
                                SWP_NOZORDER | SWP_NOACTIVATE | SWP_FRAMECHANGED | SWP_SHOWWINDOW) != FALSE;
    if (!configured) return FailWindowConfiguration(dataRoot, pid, "position_failed", GetLastError());
  }

  SetLastError(ERROR_SUCCESS);
  const LONG_PTR appliedStyle = GetWindowLongPtrW(window, GWL_STYLE);
  if (!appliedStyle && GetLastError() != ERROR_SUCCESS) {
    return FailWindowConfiguration(dataRoot, pid, "style_verify_failed", GetLastError());
  }
  if ((appliedStyle & WS_THICKFRAME) != 0) {
    return FailWindowConfiguration(dataRoot, pid, "style_not_fixed");
  }
  if (!CompleteWindowConfiguration()) {
    return FailWindowConfiguration(dataRoot, pid, "contract_signal_failed", GetLastError());
  }
  return true;
}

bool MaintainChromiumWindow(DWORD pid, const fs::path& dataRoot) {
  WindowSearch search{pid, nullptr};
  EnumWindows(FindChromiumWindow, reinterpret_cast<LPARAM>(&search));
  if (!search.window) return true;

  HWND window = search.window;
  SetLastError(ERROR_SUCCESS);
  const LONG_PTR style = GetWindowLongPtrW(window, GWL_STYLE);
  if (!style && GetLastError() != ERROR_SUCCESS) {
    return FailWindowConfiguration(dataRoot, pid, "maintain_style_read_failed", GetLastError());
  }
  const LONG_PTR fixedStyle = (style & ~static_cast<LONG_PTR>(WS_THICKFRAME)) |
                              WS_SYSMENU | WS_MINIMIZEBOX | WS_MAXIMIZEBOX;
  const bool styleChanged = fixedStyle != style;
  if (styleChanged) {
    SetLastError(ERROR_SUCCESS);
    if (!SetWindowLongPtrW(window, GWL_STYLE, fixedStyle) && GetLastError() != ERROR_SUCCESS) {
      return FailWindowConfiguration(dataRoot, pid, "maintain_style_write_failed", GetLastError());
    }
  }

  if (IsIconic(window) || IsZoomed(window)) {
    if (styleChanged &&
        !SetWindowPos(window, nullptr, 0, 0, 0, 0,
                      SWP_NOMOVE | SWP_NOSIZE | SWP_NOZORDER | SWP_NOACTIVATE | SWP_FRAMECHANGED)) {
      return FailWindowConfiguration(dataRoot, pid, "maintain_frame_failed", GetLastError());
    }
    return true;
  }

  const UINT dpi = GetDpiForWindow(window);
  if (!dpi) return FailWindowConfiguration(dataRoot, pid, "maintain_dpi_failed", GetLastError());
  const int width = MulDiv(kWindowWidth, static_cast<int>(dpi), USER_DEFAULT_SCREEN_DPI);
  const int height = MulDiv(kWindowHeight, static_cast<int>(dpi), USER_DEFAULT_SCREEN_DPI);
  HMONITOR monitor = MonitorFromWindow(window, MONITOR_DEFAULTTONEAREST);
  MONITORINFO info{};
  info.cbSize = sizeof(info);
  if (!monitor || !GetMonitorInfoW(monitor, &info)) {
    return FailWindowConfiguration(dataRoot, pid, "maintain_monitor_failed", GetLastError());
  }
  const int availableWidth = info.rcWork.right - info.rcWork.left;
  const int availableHeight = info.rcWork.bottom - info.rcWork.top;
  if (availableWidth < width || availableHeight < height) {
    if (styleChanged &&
        !SetWindowPos(window, nullptr, 0, 0, 0, 0,
                      SWP_NOMOVE | SWP_NOSIZE | SWP_NOZORDER | SWP_NOACTIVATE | SWP_FRAMECHANGED)) {
      return FailWindowConfiguration(dataRoot, pid, "maintain_frame_failed", GetLastError());
    }
    ShowWindow(window, SW_MAXIMIZE);
    return true;
  }

  RECT rect{};
  if (!GetWindowRect(window, &rect)) {
    return FailWindowConfiguration(dataRoot, pid, "maintain_rect_failed", GetLastError());
  }
  const bool sizeChanged = rect.right - rect.left != width || rect.bottom - rect.top != height;
  if (styleChanged || sizeChanged) {
    UINT flags = SWP_NOZORDER | SWP_NOACTIVATE;
    if (styleChanged) flags |= SWP_FRAMECHANGED;
    if (!SetWindowPos(window, nullptr, rect.left, rect.top, width, height, flags)) {
      return FailWindowConfiguration(dataRoot, pid, "maintain_position_failed", GetLastError());
    }
    Log(dataRoot, "window", "maintained", 0, pid);
  }
  return true;
}

int RunLauncher(int argc) {
  if (argc != 1) {
    Inform(L"CGC-PMS 启动器不接受命令行参数。", MB_OK | MB_ICONERROR);
    return kInvalidInvocation;
  }
  const fs::path module = ModuleDirectory();
  const fs::path dataRoot = LocalDataRoot();
  if (module.empty() || dataRoot.empty()) return kLaunchFailed;
  Log(dataRoot, "startup", "begin");

#ifdef CGCPMS_CONTRACT_TEST
  const std::wstring mutexName = ContractObjectName(kMutexName);
  if (mutexName.empty()) return kLaunchFailed;
  UniqueHandle mutex(CreateMutexW(nullptr, TRUE, mutexName.c_str()));
#else
  UniqueHandle mutex(CreateMutexW(nullptr, TRUE, kMutexName));
#endif
  if (!mutex) return kLaunchFailed;
  if (GetLastError() == ERROR_ALREADY_EXISTS) {
    Inform(L"CGC-PMS 已在运行。", MB_OK | MB_ICONINFORMATION);
    Log(dataRoot, "single_instance", "duplicate");
    return kAlreadyRunning;
  }

  const fs::path statePath = dataRoot / kRuntimeDir / L"launcher-state.json";
  if (ExistingBrowserAlive(statePath)) {
    Inform(L"上次启动的 CGC-PMS 浏览器仍在运行，请先关闭后重试。", MB_OK | MB_ICONWARNING);
    Log(dataRoot, "state", "browser_alive");
    return kOrphanBrowserRunning;
  }

  const fs::path chromiumDir = module / L"chromium";
  const fs::path chromiumExe = chromiumDir / L"chrome.exe";
  if (!fs::is_regular_file(chromiumExe)) {
    Inform(L"缺少 chromium\\chrome.exe，请重新校验发行目录。", MB_OK | MB_ICONERROR);
    Log(dataRoot, "runtime", "missing", ERROR_FILE_NOT_FOUND);
    return kRuntimeMissing;
  }
  if (!RuntimePathIsSafe(module, chromiumDir, chromiumExe)) {
    Inform(L"Chromium 运行时路径不安全。", MB_OK | MB_ICONERROR);
    Log(dataRoot, "runtime", "unsafe_path");
    return kUnsafeRuntimePath;
  }

  if (!WaitForHealth()) {
    Inform(L"本地 CGC-PMS 服务未就绪。请运行 scripts\\start-dev.bat 后重试。", MB_OK | MB_ICONWARNING);
    Log(dataRoot, "health", "failed");
    return kHealthFailed;
  }

  const int major = ChromiumMajor(chromiumExe);
  const fs::path profile = dataRoot / kProfileDir / (L"chromium-" + std::to_wstring(major)) / L"UserData";
  std::error_code ec;
  fs::create_directories(profile, ec);
  if (ec) {
    Log(dataRoot, "profile", "create_failed", ec.value());
    return kLaunchFailed;
  }

  const std::vector<std::wstring> args = {
      ExtendedWin32Path(chromiumExe),
      std::wstring(L"--app=") + kAppUrl,
      std::wstring(L"--user-data-dir=") + profile.wstring(),
      L"--no-first-run",
      L"--no-default-browser-check",
      L"--disable-sync",
      L"--window-size=1440,1080",
  };
  std::wstring command;
  for (const auto& arg : args) {
    if (!command.empty()) command.push_back(L' ');
    command += QuoteArgument(arg);
  }
  std::vector<wchar_t> mutableCommand(command.begin(), command.end());
  mutableCommand.push_back(L'\0');
  STARTUPINFOW startup{};
  startup.cb = sizeof(startup);
  PROCESS_INFORMATION process{};
  const std::wstring chromiumApiPath = ExtendedWin32Path(chromiumExe);
  if (!CreateProcessW(chromiumApiPath.c_str(), mutableCommand.data(), nullptr, nullptr, FALSE, 0,
                      nullptr, nullptr, &startup, &process)) {
    DWORD code = GetLastError();
    const std::wstring message = L"无法启动 Chromium。请校验发行目录。错误码：" + std::to_wstring(code);
    Inform(message.c_str(), MB_OK | MB_ICONERROR);
    Log(dataRoot, "browser", "create_failed", code);
    return kLaunchFailed;
  }
  UniqueHandle processHandle(process.hProcess);
  UniqueHandle threadHandle(process.hThread);
  if (!ConfigureChromiumWindow(process.dwProcessId, processHandle.value, dataRoot)) {
    TerminateProcess(processHandle.value, kLaunchFailed);
    WaitForSingleObject(processHandle.value, 5000);
    Inform(L"无法配置 CGC-PMS 窗口。请校验 Chromium 运行时。", MB_OK | MB_ICONERROR);
    Log(dataRoot, "window", "configure_failed", GetLastError(), process.dwProcessId);
    return kLaunchFailed;
  }
  Log(dataRoot, "window", "configured", 0, process.dwProcessId);
  FILETIME created{}, exited{}, kernel{}, user{};
  uint64_t createdValue = GetProcessTimes(processHandle.value, &created, &exited, &kernel, &user)
                              ? FileTimeValue(created) : 0;
  if (!createdValue || !WriteState(statePath, process.dwProcessId, createdValue, major)) {
    TerminateProcess(processHandle.value, kLaunchFailed);
    WaitForSingleObject(processHandle.value, 5000);
    Log(dataRoot, "state", "write_failed", GetLastError(), process.dwProcessId);
    return kLaunchFailed;
  }
  Log(dataRoot, "browser", "started", 0, process.dwProcessId);
  DWORD waitResult = WAIT_TIMEOUT;
  while ((waitResult = WaitForSingleObject(processHandle.value, kWindowMaintainMs)) == WAIT_TIMEOUT) {
    if (!MaintainChromiumWindow(process.dwProcessId, dataRoot)) {
      TerminateProcess(processHandle.value, kLaunchFailed);
      WaitForSingleObject(processHandle.value, 5000);
      fs::remove(statePath, ec);
      return kLaunchFailed;
    }
  }
  if (waitResult == WAIT_FAILED) {
    TerminateProcess(processHandle.value, kLaunchFailed);
    WaitForSingleObject(processHandle.value, 5000);
    fs::remove(statePath, ec);
    Log(dataRoot, "browser", "wait_failed", GetLastError(), process.dwProcessId);
    return kLaunchFailed;
  }
  DWORD browserExit = 0;
  GetExitCodeProcess(processHandle.value, &browserExit);
  fs::remove(statePath, ec);
  Log(dataRoot, "browser", browserExit == 0 ? "exited" : "failed", browserExit, process.dwProcessId);
  return browserExit == 0 ? kOk : kBrowserFailed;
}

#ifdef CGCPMS_CONTRACT_TEST
int wmain(int argc, wchar_t**) { return RunLauncher(argc); }
#else
int WINAPI wWinMain(HINSTANCE, HINSTANCE, PWSTR, int) {
  return RunLauncher(__argc);
}
#endif
