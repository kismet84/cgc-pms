#define UNICODE
#define _UNICODE
#include <windows.h>
#include <filesystem>
#include <fstream>
#include <sstream>
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

LRESULT CALLBACK WindowProc(HWND window, UINT message, WPARAM wParam, LPARAM lParam) {
  if (message == WM_DESTROY) {
    PostQuitMessage(0);
    return 0;
  }
  return DefWindowProcW(window, message, wParam, lParam);
}

void PumpMessages(DWORD durationMs) {
  const ULONGLONG deadline = GetTickCount64() + durationMs;
  do {
    MSG message{};
    while (PeekMessageW(&message, nullptr, 0, 0, PM_REMOVE)) {
      TranslateMessage(&message);
      DispatchMessageW(&message);
    }
    Sleep(10);
  } while (GetTickCount64() < deadline);
}

bool WaitForFixedFrame(HWND window, DWORD timeoutMs) {
  const ULONGLONG deadline = GetTickCount64() + timeoutMs;
  do {
    PumpMessages(20);
    if ((GetWindowLongPtrW(window, GWL_STYLE) & WS_THICKFRAME) == 0) {
      PumpMessages(200);
      return true;
    }
  } while (GetTickCount64() < deadline);
  return false;
}

void WriteWindowEvidence(const fs::path& path, HWND window, bool configured) {
  const bool initialMaximized = IsZoomed(window) != FALSE;
  if (initialMaximized) {
    ShowWindow(window, SW_RESTORE);
    PumpMessages(100);
  }
  RECT normal{};
  GetWindowRect(window, &normal);
  const LONG_PTR style = GetWindowLongPtrW(window, GWL_STYLE);
  ShowWindow(window, SW_MAXIMIZE);
  PumpMessages(100);
  const bool maximized = IsZoomed(window) != FALSE;
  ShowWindow(window, SW_RESTORE);
  PumpMessages(100);
  RECT restored{};
  GetWindowRect(window, &restored);
  const UINT dpi = GetDpiForWindow(window);
  auto logical = [dpi](LONG value) {
    return MulDiv(value, USER_DEFAULT_SCREEN_DPI, static_cast<int>(dpi ? dpi : USER_DEFAULT_SCREEN_DPI));
  };
  std::ofstream evidence(path, std::ios::trunc);
  evidence << "configured=" << (configured ? 1 : 0) << '\n'
           << "initialMaximized=" << (initialMaximized ? 1 : 0) << '\n'
           << "style=" << static_cast<unsigned long long>(style) << '\n'
           << "width=" << logical(normal.right - normal.left) << '\n'
           << "height=" << logical(normal.bottom - normal.top) << '\n'
           << "restoredWidth=" << logical(restored.right - restored.left) << '\n'
           << "restoredHeight=" << logical(restored.bottom - restored.top) << '\n'
           << "maximized=" << (maximized ? 1 : 0) << '\n';
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

  WNDCLASSW windowClass{};
  windowClass.lpfnWndProc = WindowProc;
  windowClass.hInstance = GetModuleHandleW(nullptr);
  windowClass.lpszClassName = L"Chrome_WidgetWin_1";
  if (!RegisterClassW(&windowClass) && GetLastError() != ERROR_CLASS_ALREADY_EXISTS) return 91;
  HWND window = CreateWindowExW(0, windowClass.lpszClassName, L"CGC-PMS Contract Browser",
                                WS_OVERLAPPEDWINDOW, CW_USEDEFAULT, CW_USEDEFAULT, 800, 600,
                                nullptr, nullptr, windowClass.hInstance, nullptr);
  if (!window) return 92;
  ShowWindow(window, SW_SHOW);
  UpdateWindow(window);
  const bool configured = WaitForFixedFrame(window, 10000);
  WriteWindowEvidence(directory / L"fake-window.txt", window, configured);
  if (fs::exists(directory / L"hold.flag")) PumpMessages(8000);
  int code = 0;
  std::ifstream exitCode(directory / L"exit-code.txt");
  if (exitCode) exitCode >> code;
  return code;
}
