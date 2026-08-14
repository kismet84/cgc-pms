#define UNICODE
#define _UNICODE
#include <windows.h>
#include <cstdlib>
#include <filesystem>
#include <fstream>
#include <sstream>
#include <string>
#include <vector>

namespace fs = std::filesystem;

constexpr wchar_t kWindowConfiguredEvent[] = L"Local\\CGCPMS.Desktop.WindowConfigured.Contract";

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

LONG gFixedNormalWidth = 0;
LONG gFixedNormalHeight = 0;

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
  if (message == WM_WINDOWPOSCHANGED) {
    const auto* position = reinterpret_cast<const WINDOWPOS*>(lParam);
    const UINT dpi = GetDpiForWindow(window);
    const int expectedWidth = MulDiv(1440, static_cast<int>(dpi ? dpi : USER_DEFAULT_SCREEN_DPI),
                                     USER_DEFAULT_SCREEN_DPI);
    const int expectedHeight = MulDiv(1080, static_cast<int>(dpi ? dpi : USER_DEFAULT_SCREEN_DPI),
                                      USER_DEFAULT_SCREEN_DPI);
    if (position && (position->flags & SWP_NOSIZE) == 0 &&
        std::abs(position->cx - expectedWidth) <= 2 &&
        std::abs(position->cy - expectedHeight) <= 2) {
      gFixedNormalWidth = position->cx;
      gFixedNormalHeight = position->cy;
    }
  }
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

bool WaitForFixedFrame(HWND window, HANDLE configuredEvent, DWORD timeoutMs) {
  const ULONGLONG deadline = GetTickCount64() + timeoutMs;
  do {
    PumpMessages(20);
    if ((GetWindowLongPtrW(window, GWL_STYLE) & WS_THICKFRAME) == 0 &&
        WaitForSingleObject(configuredEvent, 0) == WAIT_OBJECT_0) {
      PumpMessages(200);
      return true;
    }
  } while (GetTickCount64() < deadline);
  return false;
}

void WriteWindowEvidence(const fs::path& path, HWND window, HWND statusBubble,
                         const RECT& initialStatusBubbleRect, LONG_PTR initialStatusBubbleStyle,
                         bool configured) {
  const bool initialMaximized = IsZoomed(window) != FALSE;
  WINDOWPLACEMENT placement{};
  placement.length = sizeof(placement);
  const bool hasPlacement = GetWindowPlacement(window, &placement) != FALSE;
  RECT normal{};
  if (initialMaximized && gFixedNormalWidth > 0 && gFixedNormalHeight > 0) {
    normal.right = gFixedNormalWidth;
    normal.bottom = gFixedNormalHeight;
  } else if (initialMaximized && hasPlacement) {
    normal.right = placement.rcNormalPosition.right - placement.rcNormalPosition.left;
    normal.bottom = placement.rcNormalPosition.bottom - placement.rcNormalPosition.top;
  } else {
    GetWindowRect(window, &normal);
  }
  const LONG_PTR style = GetWindowLongPtrW(window, GWL_STYLE);
  const bool maximizeSupported = (style & WS_MAXIMIZEBOX) != 0;
  RECT restored{};
  if (gFixedNormalWidth > 0 && gFixedNormalHeight > 0) {
    restored.right = gFixedNormalWidth;
    restored.bottom = gFixedNormalHeight;
  } else if (hasPlacement) {
    restored.right = placement.rcNormalPosition.right - placement.rcNormalPosition.left;
    restored.bottom = placement.rcNormalPosition.bottom - placement.rcNormalPosition.top;
  } else {
    restored = normal;
  }
  const UINT dpi = GetDpiForWindow(window);
  const int expectedWidth = MulDiv(1440, static_cast<int>(dpi ? dpi : USER_DEFAULT_SCREEN_DPI),
                                   USER_DEFAULT_SCREEN_DPI);
  const int expectedHeight = MulDiv(1080, static_cast<int>(dpi ? dpi : USER_DEFAULT_SCREEN_DPI),
                                    USER_DEFAULT_SCREEN_DPI);
  MONITORINFO monitorInfo{};
  monitorInfo.cbSize = sizeof(monitorInfo);
  const HMONITOR monitor = MonitorFromWindow(window, MONITOR_DEFAULTTONEAREST);
  const bool hasMonitor = monitor && GetMonitorInfoW(monitor, &monitorInfo) != FALSE;
  const bool smallWorkArea = hasMonitor &&
                             (monitorInfo.rcWork.right - monitorInfo.rcWork.left < expectedWidth ||
                              monitorInfo.rcWork.bottom - monitorInfo.rcWork.top < expectedHeight);
  auto logical = [dpi](LONG value) {
    return MulDiv(value, USER_DEFAULT_SCREEN_DPI, static_cast<int>(dpi ? dpi : USER_DEFAULT_SCREEN_DPI));
  };
  RECT statusBubbleRect{};
  GetWindowRect(statusBubble, &statusBubbleRect);
  const LONG_PTR statusBubbleStyle = GetWindowLongPtrW(statusBubble, GWL_STYLE);
  std::ofstream evidence(path, std::ios::trunc);
  evidence << "configured=" << (configured ? 1 : 0) << '\n'
           << "initialMaximized=" << (initialMaximized ? 1 : 0) << '\n'
           << "smallWorkArea=" << (smallWorkArea ? 1 : 0) << '\n'
           << "style=" << static_cast<unsigned long long>(style) << '\n'
           << "width=" << logical(normal.right - normal.left) << '\n'
           << "height=" << logical(normal.bottom - normal.top) << '\n'
           << "restoredWidth=" << logical(restored.right - restored.left) << '\n'
           << "restoredHeight=" << logical(restored.bottom - restored.top) << '\n'
           << "maximized=" << (maximizeSupported ? 1 : 0) << '\n'
           << "statusBubbleUnchanged="
           << (statusBubbleStyle == initialStatusBubbleStyle &&
                       statusBubbleRect.right - statusBubbleRect.left ==
                           initialStatusBubbleRect.right - initialStatusBubbleRect.left &&
                       statusBubbleRect.bottom - statusBubbleRect.top ==
                           initialStatusBubbleRect.bottom - initialStatusBubbleRect.top
                   ? 1
                   : 0)
           << '\n';
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

  HWND statusBubble = CreateWindowExW(WS_EX_TOOLWINDOW, windowClass.lpszClassName, L"",
                                      WS_POPUP, 0, 0, 240, 28, window, nullptr,
                                      windowClass.hInstance, nullptr);
  if (!statusBubble) return 96;
  RECT initialStatusBubbleRect{};
  LONG_PTR initialStatusBubbleStyle = 0;

  windowClass.lpszClassName = L"Chrome_WidgetWin_0";
  if (!RegisterClassW(&windowClass) && GetLastError() != ERROR_CLASS_ALREADY_EXISTS) return 94;
  HWND decoy = CreateWindowExW(0, windowClass.lpszClassName, L"", WS_OVERLAPPEDWINDOW,
                               CW_USEDEFAULT, CW_USEDEFAULT, 640, 480, nullptr, nullptr,
                               windowClass.hInstance, nullptr);
  if (!decoy) return 95;
  const std::wstring eventName = ContractObjectName(kWindowConfiguredEvent);
  if (eventName.empty()) return 93;
  HANDLE configuredEvent = CreateEventW(nullptr, TRUE, FALSE, eventName.c_str());
  if (!configuredEvent) return 93;
  const bool configured = WaitForFixedFrame(window, configuredEvent, 10000);
  if (configured) {
    RECT rootRect{};
    GetWindowRect(window, &rootRect);
    SetWindowPos(statusBubble, nullptr, rootRect.left, rootRect.bottom - 28, 240, 28,
                 SWP_NOZORDER | SWP_NOACTIVATE);
    GetWindowRect(statusBubble, &initialStatusBubbleRect);
    initialStatusBubbleStyle = GetWindowLongPtrW(statusBubble, GWL_STYLE);
    SetWindowLongPtrW(window, GWL_STYLE, GetWindowLongPtrW(window, GWL_STYLE) | WS_THICKFRAME);
    if (IsZoomed(window) == FALSE) {
      SetWindowPos(window, nullptr, 0, 0, 1000, 700, SWP_NOMOVE | SWP_NOZORDER | SWP_NOACTIVATE |
                                                        SWP_FRAMECHANGED);
    }
    PumpMessages(1000);
  }
  WriteWindowEvidence(directory / L"fake-window.txt", window, statusBubble,
                      initialStatusBubbleRect, initialStatusBubbleStyle, configured);
  CloseHandle(configuredEvent);
  if (fs::exists(directory / L"hold.flag")) PumpMessages(8000);
  int code = 0;
  std::ifstream exitCode(directory / L"exit-code.txt");
  if (exitCode) exitCode >> code;
  return code;
}
