using System;
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

        protected override void OnCreate(Bundle bundle)
        {
            base.OnCreate(bundle);
            Instance = this;
            
            try
            {
                // Launch SMAPI in a separate thread to avoid blocking OnCreate (ANR)
                var t = new System.Threading.Thread(() => 
                {
                    try 
                    {
                        StardewModdingAPI.Program.Main(new string[0]);
                    }
                    catch (Exception ex)
                    {
                        Android.Util.Log.Error("SMAPI", $"Bridge Thread Failed: {ex}");
                    }
                });
                t.IsBackground = true;
                t.Start();
            }
            catch (Exception ex)
            {
                Android.Util.Log.Error("SMAPI", $"Bridge Failed: {ex}");
                throw;
            }
        }

        public void SetView(Android.Views.View view)
        {
            RunOnUiThread(() => SetContentView(view));
        }
    }
}
