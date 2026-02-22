using System;
using System.IO;
using Android.App;
using Android.Content;
using Android.Content.PM;
using Android.OS;
using Microsoft.Xna.Framework;

namespace com.chucklefish.stardewvalley
{
    /**
     * StardewValley Activity Shim.
     * This class is injected via the modded Stardew Valley.dll (SMAPI).
     * It intercepts the game launch and bootstraps the SMAPI engine.
     */
    [Activity(Label = "Stardew Valley", 
              Name = "com.chucklefish.stardewvalley.StardewValley",
              MainLauncher = true,
              Icon = "@drawable/icon",
              Theme = "@style/Theme.AppCompat.NoActionBar",
              AlwaysRetainTaskState = true,
              LaunchMode = LaunchMode.SingleInstance,
              ScreenOrientation = ScreenOrientation.SensorLandscape,
              ConfigurationChanges = ConfigChanges.Orientation | ConfigChanges.Keyboard | ConfigChanges.KeyboardHidden | ConfigChanges.ScreenSize)]
    public class StardewValley : AndroidGameActivity
    {
        public static StardewValley Instance { get; private set; }
        public static Android.Views.View CurrentView { get; private set; }
        private static bool IsSmapiRunning = false;

        static StardewValley()
        {
            try { File.AppendAllText("/sdcard/PotataSMAPI/bridge_log.txt", $"[{DateTime.Now:HH:mm:ss}] [Static] Bridge Class Loaded.\n"); } catch {}
        }

        private void Log(string msg)
        {
            try
            {
                File.AppendAllText("/sdcard/PotataSMAPI/bridge_log.txt", $"[{DateTime.Now:HH:mm:ss}] [Instance] {msg}\n");
            }
            catch {}
            Android.Util.Log.Debug("SMAPI_Bridge", msg);
        }

        protected override void OnCreate(Bundle bundle)
        {
            Log("OnCreate fired.");
            base.OnCreate(bundle);
            Instance = this;
            
            if (CurrentView != null)
            {
                Log("Restoring existing game view...");
                SetContentView(CurrentView);
                return;
            }

            if (IsSmapiRunning)
            {
                Log("SMAPI engine already active. Waiting for View signal...");
                return;
            }

            try
            {
                Log("Initial Boot: Bootstrapping SMAPI thread...");
                IsSmapiRunning = true;
                var t = new System.Threading.Thread(() => 
                {
                    try 
                    {
                        Log("SMAPI Thread: Calling Program.Main...");
                        StardewModdingAPI.Program.Main(new string[0]);
                        Log("SMAPI Thread: Program.Main exited.");
                    }
                    catch (Exception ex)
                    {
                        Log($"SMAPI Thread CRASH: {ex}");
                        Android.Util.Log.Error("SMAPI", $"SMAPI Thread CRASH: {ex}");
                    }
                });
                t.IsBackground = true;
                t.Start();
                Log("SMAPI Thread started.");
            }
            catch (Exception ex)
            {
                Log($"Bridge Startup FAIL: {ex}");
                Android.Util.Log.Error("SMAPI", $"Bridge Startup FAIL: {ex}");
                throw;
            }
        }

        public void SetView(Android.Views.View view)
        {
            Log("SetView signal received from Engine.");
            CurrentView = view;
            RunOnUiThread(() => 
            {
                Log("Applying Game View to Activity...");
                SetContentView(view);
            });
        }
    }
}
