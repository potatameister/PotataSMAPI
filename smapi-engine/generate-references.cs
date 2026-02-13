using System;
using System.IO;
using System.Reflection;
using System.Reflection.Emit;

public class Program {
    public static void Main(string[] args) {
        string[] dlls = {
            "Stardew Valley.dll",
            "StardewValley.GameData.dll",
            "BmFont.dll",
            "GalaxyCSharp.dll",
            "Lidgren.Network.dll",
            "MonoGame.Framework.dll",
            "SkiaSharp.dll",
            "xTile.dll"
        };

        foreach (var dll in dlls) {
            string name = Path.GetFileNameWithoutExtension(dll);
            AssemblyName assemblyName = new AssemblyName(name);
            AssemblyBuilder ab = AssemblyBuilder.DefineDynamicAssembly(assemblyName, AssemblyBuilderAccess.RunAndSave);
            ModuleBuilder mb = ab.DefineDynamicModule(name, dll);
            
            // Create a dummy Game1 class for Stardew Valley.dll
            if (name == "Stardew Valley") {
                TypeBuilder tb = mb.DefineType("StardewValley.Game1", TypeAttributes.Public);
                tb.DefineField("version", typeof(string), FieldAttributes.Public | FieldAttributes.Static);
                tb.CreateType();
            }

            ab.Save(dll);
            Console.WriteLine("Generated reference: " + dll);
        }
    }
}
