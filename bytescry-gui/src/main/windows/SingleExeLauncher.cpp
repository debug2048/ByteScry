#include <windows.h>
#include <shellapi.h>
#include <shldisp.h>

#include <algorithm>
#include <cstdint>
#include <chrono>
#include <cstdlib>
#include <cwchar>
#include <filesystem>
#include <fstream>
#include <iomanip>
#include <sstream>
#include <stdexcept>
#include <string>
#include <thread>
#include <vector>

namespace {

constexpr const char* Marker = "BYTE-SCRY-SFX-ZIP-V1";
constexpr size_t MarkerLength = 20;

std::wstring QuoteArgument(const std::wstring& argument);

std::wstring Utf8ToWide(const std::string& value) {
    if (value.empty()) {
        return L"";
    }
    int length = MultiByteToWideChar(CP_UTF8, 0, value.data(), static_cast<int>(value.size()), nullptr, 0);
    if (length <= 0) {
        throw std::runtime_error("Failed to convert UTF-8 text.");
    }
    std::wstring output(static_cast<size_t>(length), L'\0');
    MultiByteToWideChar(CP_UTF8, 0, value.data(), static_cast<int>(value.size()), output.data(), length);
    return output;
}

std::wstring LastErrorMessage(const wchar_t* prefix) {
    DWORD error = GetLastError();
    wchar_t* buffer = nullptr;
    FormatMessageW(FORMAT_MESSAGE_ALLOCATE_BUFFER | FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS,
                   nullptr, error, 0, reinterpret_cast<LPWSTR>(&buffer), 0, nullptr);
    std::wstring message(prefix);
    if (buffer != nullptr) {
        message += L": ";
        message += buffer;
        LocalFree(buffer);
    }
    return message;
}

std::wstring ModulePath() {
    std::vector<wchar_t> buffer(MAX_PATH);
    while (true) {
        DWORD written = GetModuleFileNameW(nullptr, buffer.data(), static_cast<DWORD>(buffer.size()));
        if (written == 0) {
            throw std::runtime_error("Failed to resolve executable path.");
        }
        if (written < buffer.size() - 1) {
            return std::wstring(buffer.data(), written);
        }
        buffer.resize(buffer.size() * 2);
    }
}

std::wstring SystemTarPath() {
    std::vector<wchar_t> buffer(MAX_PATH);
    UINT written = GetSystemDirectoryW(buffer.data(), static_cast<UINT>(buffer.size()));
    if (written == 0 || written >= buffer.size()) {
        return L"tar.exe";
    }
    std::filesystem::path tarPath(std::wstring(buffer.data(), written));
    tarPath /= L"tar.exe";
    if (std::filesystem::exists(tarPath)) {
        return tarPath.wstring();
    }
    return L"tar.exe";
}

int RunHiddenProcess(const std::wstring& command, const std::filesystem::path& workingDirectory) {
    STARTUPINFOW startupInfo{};
    startupInfo.cb = sizeof(startupInfo);
    PROCESS_INFORMATION processInfo{};
    std::wstring mutableCommand = command;
    std::wstring cwd = workingDirectory.wstring();

    if (!CreateProcessW(nullptr, mutableCommand.data(), nullptr, nullptr, FALSE,
                        CREATE_NO_WINDOW, nullptr, cwd.c_str(), &startupInfo, &processInfo)) {
        return -1;
    }
    WaitForSingleObject(processInfo.hProcess, INFINITE);
    DWORD exitCode = 1;
    GetExitCodeProcess(processInfo.hProcess, &exitCode);
    CloseHandle(processInfo.hThread);
    CloseHandle(processInfo.hProcess);
    return static_cast<int>(exitCode);
}

std::vector<uint8_t> ReadPayload(const std::wstring& selfPath) {
    std::ifstream stream(selfPath, std::ios::binary | std::ios::ate);
    if (!stream) {
        throw std::runtime_error("Could not open ByteScry executable.");
    }

    std::streamoff fileSize = stream.tellg();
    if (fileSize < static_cast<std::streamoff>(MarkerLength + sizeof(int64_t))) {
        throw std::runtime_error("This executable does not contain an embedded ByteScry payload.");
    }

    std::vector<char> marker(MarkerLength);
    stream.seekg(fileSize - static_cast<std::streamoff>(MarkerLength));
    stream.read(marker.data(), static_cast<std::streamsize>(marker.size()));
    if (!stream || std::string(marker.begin(), marker.end()) != Marker) {
        throw std::runtime_error("This executable contains an invalid ByteScry payload marker.");
    }

    int64_t payloadLength = 0;
    stream.seekg(fileSize - static_cast<std::streamoff>(MarkerLength + sizeof(payloadLength)));
    stream.read(reinterpret_cast<char*>(&payloadLength), sizeof(payloadLength));
    std::streamoff payloadOffset = fileSize - static_cast<std::streamoff>(MarkerLength + sizeof(payloadLength) + payloadLength);
    if (!stream || payloadLength <= 0 || payloadOffset <= 0) {
        throw std::runtime_error("This executable contains an invalid ByteScry payload length.");
    }

    std::vector<uint8_t> payload(static_cast<size_t>(payloadLength));
    stream.seekg(payloadOffset);
    stream.read(reinterpret_cast<char*>(payload.data()), payloadLength);
    if (!stream) {
        throw std::runtime_error("Could not read embedded ByteScry payload.");
    }
    return payload;
}

std::wstring PayloadHash(const std::vector<uint8_t>& payload) {
    uint64_t hash = 1469598103934665603ull;
    for (uint8_t byte : payload) {
        hash ^= byte;
        hash *= 1099511628211ull;
    }
    std::wstringstream stream;
    stream << std::hex << std::setfill(L'0') << std::setw(16) << hash;
    return stream.str();
}

std::filesystem::path LocalAppDataBaseRoot() {
    wchar_t* localAppData = nullptr;
    size_t length = 0;
    if (_wdupenv_s(&localAppData, &length, L"LOCALAPPDATA") != 0 || localAppData == nullptr || length == 0) {
        throw std::runtime_error("LOCALAPPDATA is not available.");
    }
    std::filesystem::path root(localAppData);
    free(localAppData);
    return root / L"ByteScry" / L"single-exe";
}

std::filesystem::path LocalAppDataRoot(const std::wstring& payloadHash) {
    return LocalAppDataBaseRoot() / payloadHash;
}

void CleanupExtractionCache(const std::filesystem::path& currentRoot) {
    std::filesystem::path baseRoot = LocalAppDataBaseRoot();
    if (!std::filesystem::exists(baseRoot)) {
        return;
    }

    std::vector<std::pair<std::filesystem::file_time_type, std::filesystem::path>> roots;
    for (const auto& entry : std::filesystem::directory_iterator(baseRoot)) {
        if (!entry.is_directory()) {
            continue;
        }
        std::error_code ignored;
        roots.emplace_back(std::filesystem::last_write_time(entry.path(), ignored), entry.path());
    }

    std::sort(roots.begin(), roots.end(), [](const auto& a, const auto& b) {
        return a.first > b.first;
    });

    constexpr size_t KeepCacheDirectories = 4;
    size_t kept = 0;
    std::filesystem::path normalizedCurrent = currentRoot.lexically_normal();
    for (const auto& root : roots) {
        if (root.second.lexically_normal() == normalizedCurrent) {
            kept++;
            continue;
        }
        if (kept < KeepCacheDirectories) {
            kept++;
            continue;
        }
        std::error_code ignored;
        std::filesystem::remove_all(root.second, ignored);
    }
}

bool IsCompleteApp(const std::filesystem::path& appExe) {
    if (appExe.empty() || !std::filesystem::exists(appExe)) {
        return false;
    }
    std::filesystem::path appRoot = appExe.parent_path().parent_path();
    return std::filesystem::exists(appRoot / L"runtime" / L"bin" / L"javaw.exe")
            && std::filesystem::exists(appRoot / L"lib")
            && std::filesystem::exists(appRoot / L"javafx");
}

std::filesystem::path FindExtractedApp(const std::filesystem::path& extractRoot) {
    if (!std::filesystem::exists(extractRoot)) {
        return {};
    }
    for (const auto& entry : std::filesystem::recursive_directory_iterator(extractRoot)) {
        if (!entry.is_regular_file()) {
            continue;
        }
        std::filesystem::path path = entry.path();
        if (_wcsicmp(path.filename().c_str(), L"bytescry.exe") == 0
                && path.parent_path().filename() == L"bin"
                && IsCompleteApp(path)) {
            return path;
        }
    }
    return {};
}

void WritePayloadZip(const std::filesystem::path& zipPath, const std::vector<uint8_t>& payload) {
    std::ofstream stream(zipPath, std::ios::binary | std::ios::trunc);
    if (!stream) {
        throw std::runtime_error("Could not create embedded payload zip.");
    }
    stream.write(reinterpret_cast<const char*>(payload.data()), static_cast<std::streamsize>(payload.size()));
    if (!stream) {
        throw std::runtime_error("Could not write embedded payload zip.");
    }
}

bool ExtractZipWithTar(const std::filesystem::path& zipPath, const std::filesystem::path& extractRoot) {
    std::wstring command = QuoteArgument(SystemTarPath())
            + L" -xf " + QuoteArgument(zipPath.wstring())
            + L" -C " + QuoteArgument(extractRoot.wstring());
    return RunHiddenProcess(command, extractRoot) == 0;
}

void ExtractZipWithShell(const std::filesystem::path& zipPath, const std::filesystem::path& extractRoot) {
    HRESULT hr = CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED);
    bool initialized = SUCCEEDED(hr);
    if (FAILED(hr) && hr != RPC_E_CHANGED_MODE) {
        throw std::runtime_error("Could not initialize Windows Shell extraction.");
    }

    IShellDispatch* shell = nullptr;
    Folder* zipFolder = nullptr;
    Folder* destFolder = nullptr;
    FolderItems* items = nullptr;

    try {
        hr = CoCreateInstance(CLSID_Shell, nullptr, CLSCTX_INPROC_SERVER, IID_PPV_ARGS(&shell));
        if (FAILED(hr) || shell == nullptr) {
            throw std::runtime_error("Could not create Windows Shell instance.");
        }

        VARIANT zipVariant;
        VariantInit(&zipVariant);
        zipVariant.vt = VT_BSTR;
        zipVariant.bstrVal = SysAllocString(zipPath.wstring().c_str());
        hr = shell->NameSpace(zipVariant, &zipFolder);
        VariantClear(&zipVariant);
        if (FAILED(hr) || zipFolder == nullptr) {
            throw std::runtime_error("Could not open embedded payload zip.");
        }

        VARIANT destVariant;
        VariantInit(&destVariant);
        destVariant.vt = VT_BSTR;
        destVariant.bstrVal = SysAllocString(extractRoot.wstring().c_str());
        hr = shell->NameSpace(destVariant, &destFolder);
        VariantClear(&destVariant);
        if (FAILED(hr) || destFolder == nullptr) {
            throw std::runtime_error("Could not open ByteScry extraction directory.");
        }

        hr = zipFolder->Items(&items);
        if (FAILED(hr) || items == nullptr) {
            throw std::runtime_error("Could not enumerate embedded payload zip.");
        }

        VARIANT itemVariant;
        VariantInit(&itemVariant);
        itemVariant.vt = VT_DISPATCH;
        itemVariant.pdispVal = items;

        VARIANT options;
        VariantInit(&options);
        options.vt = VT_I4;
        options.lVal = 4 | 16 | 512 | 1024;

        hr = destFolder->CopyHere(itemVariant, options);
        VariantClear(&options);
        if (FAILED(hr)) {
            throw std::runtime_error("Could not extract embedded ByteScry payload.");
        }
    } catch (...) {
        if (items) items->Release();
        if (destFolder) destFolder->Release();
        if (zipFolder) zipFolder->Release();
        if (shell) shell->Release();
        if (initialized) CoUninitialize();
        throw;
    }

    if (items) items->Release();
    if (destFolder) destFolder->Release();
    if (zipFolder) zipFolder->Release();
    if (shell) shell->Release();
    if (initialized) CoUninitialize();
}

std::filesystem::path EnsureExtracted(const std::vector<uint8_t>& payload) {
    std::filesystem::path extractRoot = LocalAppDataRoot(PayloadHash(payload));
    std::filesystem::path appExe = FindExtractedApp(extractRoot);
    if (!appExe.empty()) {
        CleanupExtractionCache(extractRoot);
        return appExe;
    }

    if (std::filesystem::exists(extractRoot)) {
        std::error_code ignored;
        std::filesystem::remove_all(extractRoot, ignored);
    }
    std::filesystem::create_directories(extractRoot);
    std::filesystem::path zipPath = extractRoot / L"payload.zip";
    WritePayloadZip(zipPath, payload);
    bool extractedSynchronously = ExtractZipWithTar(zipPath, extractRoot);
    if (!extractedSynchronously) {
        ExtractZipWithShell(zipPath, extractRoot);
    }
    std::error_code ignored;
    if (extractedSynchronously) {
        std::filesystem::remove(zipPath, ignored);
    }

    for (int i = 0; i < 480; i++) {
        appExe = FindExtractedApp(extractRoot);
        if (!appExe.empty()) {
            std::filesystem::remove(zipPath, ignored);
            CleanupExtractionCache(extractRoot);
            return appExe;
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(250));
    }
    std::filesystem::remove(zipPath, ignored);
    throw std::runtime_error("Embedded ByteScry application was not found after extraction.");
}

std::wstring QuoteArgument(const std::wstring& argument) {
    if (argument.empty()) {
        return L"\"\"";
    }
    if (argument.find_first_of(L" \t\"") == std::wstring::npos) {
        return argument;
    }
    std::wstring quoted = L"\"";
    size_t backslashes = 0;
    for (wchar_t ch : argument) {
        if (ch == L'\\') {
            backslashes++;
        } else if (ch == L'"') {
            quoted.append(backslashes * 2 + 1, L'\\');
            quoted.push_back(ch);
            backslashes = 0;
        } else {
            quoted.append(backslashes, L'\\');
            quoted.push_back(ch);
            backslashes = 0;
        }
    }
    quoted.append(backslashes * 2, L'\\');
    quoted.push_back(L'"');
    return quoted;
}

std::wstring ForwardedArguments() {
    int argc = 0;
    LPWSTR* argv = CommandLineToArgvW(GetCommandLineW(), &argc);
    if (argv == nullptr) {
        return L"";
    }
    std::wstring forwarded;
    for (int i = 1; i < argc; i++) {
        if (!forwarded.empty()) {
            forwarded.push_back(L' ');
        }
        forwarded += QuoteArgument(argv[i]);
    }
    LocalFree(argv);
    return forwarded;
}

void LaunchApp(const std::filesystem::path& appExe) {
    std::wstring command = QuoteArgument(appExe.wstring());
    std::wstring args = ForwardedArguments();
    if (!args.empty()) {
        command.push_back(L' ');
        command += args;
    }

    STARTUPINFOW startupInfo{};
    startupInfo.cb = sizeof(startupInfo);
    PROCESS_INFORMATION processInfo{};
    std::wstring workingDirectory = appExe.parent_path().wstring();

    if (!CreateProcessW(nullptr, command.data(), nullptr, nullptr, FALSE, 0, nullptr,
                        workingDirectory.c_str(), &startupInfo, &processInfo)) {
        throw std::runtime_error("Could not start embedded ByteScry application.");
    }
    CloseHandle(processInfo.hThread);
    CloseHandle(processInfo.hProcess);
}

void ShowError(const std::wstring& message) {
    MessageBoxW(nullptr, message.c_str(), L"ByteScry startup failed", MB_OK | MB_ICONERROR);
}

} // namespace

int WINAPI wWinMain(HINSTANCE, HINSTANCE, LPWSTR, int) {
    try {
        std::vector<uint8_t> payload = ReadPayload(ModulePath());
        LaunchApp(EnsureExtracted(payload));
        return 0;
    } catch (const std::exception& ex) {
        ShowError(Utf8ToWide(ex.what()));
        return 1;
    } catch (...) {
        ShowError(L"Unexpected startup failure.");
        return 1;
    }
}
