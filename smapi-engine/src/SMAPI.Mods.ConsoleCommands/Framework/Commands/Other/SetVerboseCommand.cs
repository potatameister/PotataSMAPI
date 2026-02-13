using System;
using System.Collections.Generic;
using System.Linq;
using StardewModdingAPI.Framework;
using StardewValley.Extensions;

namespace StardewModdingAPI.Mods.ConsoleCommands.Framework.Commands.Other;

/// <summary>A command which toggles verbose mode in the SMAPI log.</summary>
internal class SetVerboseCommand : ConsoleCommand
{
    /*********
    ** Fields
    *********/
    /// <summary>The mod register with which to validate mod IDs.</summary>
    private readonly IModRegistry ModRegistry;


    /*********
    ** Public methods
    *********/
    /// <summary>Construct an instance.</summary>
    /// <param name="modRegistry">The mod register with which to validate mod IDs.</param>
    public SetVerboseCommand(IModRegistry modRegistry)
        : base(
            name: "set_verbose",
            description:
            """
            Toggles whether more detailed information is written to the SMAPI log file (and console in developer mode). This may impact performance. This doesn't affect mods manually set to verbose in the config file.

            Usage: set_verbose
            Toggles verbose logging for SMAPI and all mods.

            Usage: set_verbose [true|false]
            Sets whether verbose logging is enabled (true) or disabled (false) for SMAPI and all mods.

            Usage: set_verbose [true|false] [modId]+
            Sets whether verbose logging is enabled (true) or disabled (false) for the specified mod IDs. You can specify 'SMAPI' to set it for SMAPI itself.
            """,
            mayNeedUpdate: true
        )
    {
        this.ModRegistry = modRegistry;
    }

    /// <summary>Handle the command.</summary>
    /// <param name="monitor">Writes messages to the console and log file.</param>
    /// <param name="command">The command name.</param>
    /// <param name="args">The command arguments.</param>
    public override void Handle(IMonitor monitor, string command, ArgumentParser args)
    {
        // parse mode
        bool setTo = !(Monitor.ForceVerboseLoggingForAll || Monitor.ForceVerboseLogging.Count > 0);
        if (args.Count > 0)
        {
            if (int.TryParse(args[0], out int numeric) && numeric is 0 or 1)
                setTo = numeric is 1;
            else if (!bool.TryParse(args[0], out setTo))
            {
                monitor.Log("Invalid argument: if specified, the first argument should be 'true' or 'false' to indicate whether to enable or disable verbose logging.");
                return;
            }
        }

        // apply
        if (args.Count is 0 or 1)
        {
            if (setTo)
            {
                Monitor.ForceVerboseLoggingForAll = true;
                Monitor.ForceVerboseLogging.Clear();
                monitor.Log("Enabled verbose logs for SMAPI and all mods.", LogLevel.Info);
            }
            else
            {
                Monitor.ForceVerboseLoggingForAll = false;
                Monitor.ForceVerboseLogging.Clear();
                monitor.Log("Reset to normal.", LogLevel.Info);
            }
        }
        else
        {
            List<string> toggled = [];
            List<string> unknown = [];

            for (int i = 1; i < args.Count; i++)
            {
                // get mod
                string modId = args[i];
                IModInfo? mod;
                if (modId.EqualsIgnoreCase("SMAPI"))
                {
                    modId = "SMAPI";
                    mod = null;
                }
                else
                {
                    mod = this.ModRegistry.Get(modId);
                    if (mod is null)
                    {
                        unknown.Add(modId);
                        continue;
                    }

                    modId = mod.Manifest.UniqueID;
                }

                // toggle
                Monitor.ForceVerboseLogging.Toggle(modId, setTo);
                toggled.Add(mod is null ? "SMAPI" : mod.Manifest.Name);
            }

            if (toggled.Count > 0)
                monitor.Log($"{(setTo ? "Enabled" : "Disabled")} verbose logging for mod{(toggled.Count > 1 ? "s" : "")} '{string.Join("', '", toggled.OrderBy(p => p, StringComparer.OrdinalIgnoreCase))}'.", LogLevel.Info);

            if (unknown.Count > 0)
            {
                bool plural = unknown.Count > 1;
                monitor.Log($"No mod{(plural ? "s" : "")} found with ID{(plural ? "s" : "")} '{string.Join("', '", unknown)}'.", LogLevel.Warn);
            }
        }
    }
}
