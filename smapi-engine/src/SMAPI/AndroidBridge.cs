using System;
using Android.App;
using Android.Content;
using Android.Content.PM;
using Android.OS;
using Microsoft.Xna.Framework;

namespace com.chucklefish.stardewvalley
{
using System;
using System.IO;
using Android.App;
using Android.Content;
using Android.Content.PM;
using Android.OS;
using Microsoft.Xna.Framework;

namespace com.chucklefish.stardewvalley
{
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

        private void Log(string msg)
        {
            try
            {
                File.AppendAllText("/sdcard/PotataSMAPI/bridge_log.txt", $"[{DateTime.Now:HH:mm:ss}] {msg}\n");
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
                Log("Restoring existing view...");
                SetContentView(CurrentView);
                return;
            }

            if (IsSmapiRunning)
            {
                Log("SMAPI is already running but View is null. Waiting...");
                return;
            }

            try
            {
                Log("Starting SMAPI thread...");
                IsSmapiRunning = true;
                var t = new System.Threading.Thread(() => 
                {
                    try 
                    {
                        Log("Calling Program.Main...");
                        StardewModdingAPI.Program.Main(new string[0]);
                        Log("Program.Main returned (unexpected).");
                    }
                    catch (Exception ex)
                    {
                        Log($"Bridge Thread Failed: {ex}");
                        Android.Util.Log.Error("SMAPI", $"Bridge Thread Failed: {ex}");
                    }
                });
                t.IsBackground = true;
                t.Start();
            }
            catch (Exception ex)
            {
                Log($"Bridge Failed: {ex}");
                Android.Util.Log.Error("SMAPI", $"Bridge Failed: {ex}");
                throw;
            }
        }

        public void SetView(Android.Views.View view)
        {
            Log("SetView called.");
            CurrentView = view;
            RunOnUiThread(() => 
            {
                Log("Setting Content View on UI Thread.");
                SetContentView(view);
            });
        }
    }
}
