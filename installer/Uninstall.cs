using System;
using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Text;

/// <summary>
/// GameRoute's local "Uninstall.exe" -- present in the install folder so
/// uninstalling doesn't require hunting through Windows Settings, matching
/// how Discord/Steam-style installers behave. It doesn't reimplement
/// removal itself; it just looks up whichever GameRoute product is
/// currently registered (by the installer's stable UpgradeCode, so this
/// keeps working across version upgrades without being rebuilt) and hands
/// off to msiexec, which is the real, Windows-Installer-registered
/// uninstall path also reachable from "Apps &amp; Features".
/// </summary>
class Uninstall
{
    // Must match the <Product UpgradeCode="..."> in Product.wxs -- this
    // does NOT change between GameRoute versions, unlike the ProductCode.
    const string UpgradeCode = "{54E8678D-60CD-4644-80E1-176C54D52A9C}";

    [DllImport("msi.dll", CharSet = CharSet.Unicode)]
    static extern int MsiEnumRelatedProducts(string lpUpgradeCode, int dwReserved, int iProductIndex, StringBuilder lpProductBuf);

    static int Main(string[] args)
    {
        var productCode = new StringBuilder(39);
        int result = MsiEnumRelatedProducts(UpgradeCode, 0, 0, productCode);
        if (result != 0)
        {
            Console.Error.WriteLine("GameRoute does not appear to be installed (or was already removed).");
            return 1;
        }

        bool quiet = Array.IndexOf(args, "/quiet") >= 0 || Array.IndexOf(args, "/S") >= 0;
        string arguments = "/x " + productCode + (quiet ? " /quiet" : "");

        try
        {
            Process.Start(new ProcessStartInfo("msiexec.exe", arguments) { UseShellExecute = true });
            return 0;
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine("Could not launch the uninstaller: " + ex.Message);
            return 1;
        }
    }
}
