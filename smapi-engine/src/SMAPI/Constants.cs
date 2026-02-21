using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Reflection;
using Mono.Cecil;
using StardewModdingAPI.Enums;
using StardewModdingAPI.Framework;
using StardewModdingAPI.Framework.ModLoading;
using StardewModdingAPI.Toolkit.Framework;
using StardewModdingAPI.Toolkit.Utilities;
using StardewValley;

namespace StardewModdingAPI;

internal static class EarlyConstants
{
    private static string GetBaseDir() => Environment.GetEnvironmentVariable("SMAPI_ANDROID_BASE_DIR") ?? "/sdcard/PotataSMAPI";

    public static string? AndroidBaseDirPath { get; set; } = GetBaseDir();

    public static string GamePath { get; } = GetBaseDir();

    public static string InternalFilesPath => Path.Combine(GetBaseDir(), "smapi-internal");

    internal static GamePlatform Platform { get; } = GamePlatform.Android;

    internal static GameFramework GameFramework { get; } = GameFramework.MonoGame;

    internal static string GameAssemblyName { get; } = "StardewValley.Vanilla";

    internal static int? LogScreenId { get; set; }

    internal static string RawApiVersion = "4.5.1";
}

public static class Constants
{
    private static string GetBaseDir() => EarlyConstants.AndroidBaseDirPath ?? "/sdcard/PotataSMAPI";

    public static ISemanticVersion ApiVersion { get; } = new Toolkit.SemanticVersion(EarlyConstants.RawApiVersion);
    public static ISemanticVersion MinimumGameVersion { get; } = new GameVersion("1.6.14");
    public static int? MinimumGameBuild { get; } = null;
    public static ISemanticVersion? MaximumGameVersion { get; } = null;
    public static GamePlatform TargetPlatform { get; } = GamePlatform.Android;
    public static GameFramework GameFramework { get; } = GameFramework.MonoGame;
    public static string GamePath { get; } = GetBaseDir();
    public static string ContentPath { get; } = Path.Combine(GetBaseDir(), "Content");
    public static string? AndroidBaseDirPath { get => GetBaseDir(); set { } }
    public static string DataPath { get; } = GetBaseDir();
    public static string LogDir { get; } = Path.Combine(GetBaseDir(), "ErrorLogs");
    public static string SavesPath { get; } = Path.Combine(GetBaseDir(), "Saves");
    public static string? SaveFolderName => null;
    public static string? CurrentSavePath => null;

    internal const bool IsDebugBuild = false;
    internal const string HomePageUrl = "https://smapi.io";
    internal static string InternalFilesPath => Path.Combine(GetBaseDir(), "smapi-internal");
    internal static string ApiConfigPath => Path.Combine(Constants.InternalFilesPath, "config.json");
    internal static string ApiUserConfigPath => Path.Combine(Constants.InternalFilesPath, "config.user.json");
    internal static string ApiModGroupConfigPath => Path.Combine(Constants.ModsPath, "SMAPI-config.json");
    internal static string ApiMetadataPath => Path.Combine(Constants.InternalFilesPath, "metadata.json");
    internal static string ApiBlacklistPath => Path.Combine(Constants.InternalFilesPath, "blacklist.json");
    internal static string ApiBlacklistFetchedPath => Path.Combine(Constants.InternalFilesPath, "blacklist-updated.json");
    internal static string? ApiBlacklistActualPath;
    internal static string LogNamePrefix { get; } = "SMAPI-";
    internal static string LogFilename { get; } = $"{Constants.LogNamePrefix}latest";
    internal static string LogExtension { get; } = "txt";
    internal static string FatalCrashLog => Path.Combine(Constants.LogDir, "SMAPI-crash.txt");
    internal static string FatalCrashMarker => Path.Combine(Constants.InternalFilesPath, "StardewModdingAPI.crash.marker");
    internal static string UpdateMarker => Path.Combine(Constants.InternalFilesPath, "StardewModdingAPI.update.marker");
    internal static string DefaultModsPath { get; } = Path.Combine(GetBaseDir(), "Mods");
    internal static string ModsPath { get; set; } = Path.Combine(GetBaseDir(), "Mods");
    internal static ISemanticVersion GameVersion { get; } = new GameVersion("1.6.14");
    internal static Platform Platform { get; } = Platform.Android;

    internal static ISemanticVersion? GetCompatibleApiVersion(ISemanticVersion version) => null;
    internal static void ConfigureAssemblyResolver(AssemblyDefinitionResolver resolver) {}
    internal static PlatformAssemblyMap GetAssemblyMap(Platform targetPlatform) => null!;
    private static string GetContentFolderPath() => Path.Combine(GetBaseDir(), "Content");
    private static string? GetSaveFolderName() => null;
    private static string? GetSaveFolderPathIfExists() => null;
    private static DirectoryInfo? GetSaveFolder() => null;
    internal static string GetBuildVersionLabel() => "1.6.14";
}
