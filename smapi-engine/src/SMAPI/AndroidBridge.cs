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
        protected override void OnCreate(Bundle bundle)
        {
            base.OnCreate(bundle);
            
            try
            {
                // Launch SMAPI
                StardewModdingAPI.Program.Main(new string[0]);
            }
            catch (Exception ex)
            {
                Android.Util.Log.Error("SMAPI", $"Bridge Failed: {ex}");
                throw;
            }
        }
    }
}
