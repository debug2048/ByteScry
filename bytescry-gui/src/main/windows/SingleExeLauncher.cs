using System;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Security.Cryptography;
using System.Text;
using System.Windows.Forms;

internal static class SingleExeLauncher
{
    private static readonly byte[] Marker = Encoding.ASCII.GetBytes("BYTE-SCRY-SFX-ZIP-V1");

    [STAThread]
    private static int Main(string[] args)
    {
        try
        {
            string selfPath = Process.GetCurrentProcess().MainModule.FileName;
            byte[] payload = ReadPayload(selfPath);
            string payloadHash = ComputeHash(payload);
            string extractRoot = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "ByteScry",
                "single-exe",
                payloadHash.Substring(0, 16));

            string appExe = FindExtractedApp(extractRoot);
            if (appExe == null)
            {
                Directory.CreateDirectory(extractRoot);
                string zipPath = Path.Combine(extractRoot, "payload.zip");
                File.WriteAllBytes(zipPath, payload);
                ZipFile.ExtractToDirectory(zipPath, extractRoot);
                File.Delete(zipPath);
                appExe = FindExtractedApp(extractRoot);
            }

            if (appExe == null)
            {
                throw new InvalidOperationException("Embedded ByteScry application was not found after extraction.");
            }

            Process.Start(new ProcessStartInfo
            {
                FileName = appExe,
                Arguments = QuoteArguments(args),
                WorkingDirectory = Path.GetDirectoryName(appExe),
                UseShellExecute = false
            });
            return 0;
        }
        catch (Exception ex)
        {
            MessageBox.Show(ex.Message, "ByteScry startup failed", MessageBoxButtons.OK, MessageBoxIcon.Error);
            return 1;
        }
    }

    private static byte[] ReadPayload(string selfPath)
    {
        using (FileStream stream = File.OpenRead(selfPath))
        {
            if (stream.Length < Marker.Length + sizeof(long))
            {
                throw new InvalidOperationException("This executable does not contain an embedded ByteScry payload.");
            }

            stream.Seek(-Marker.Length, SeekOrigin.End);
            byte[] marker = new byte[Marker.Length];
            ReadFully(stream, marker);
            if (!marker.SequenceEqual(Marker))
            {
                throw new InvalidOperationException("This executable contains an invalid ByteScry payload marker.");
            }

            stream.Seek(-(Marker.Length + sizeof(long)), SeekOrigin.End);
            byte[] lengthBytes = new byte[sizeof(long)];
            ReadFully(stream, lengthBytes);
            long payloadLength = BitConverter.ToInt64(lengthBytes, 0);
            long payloadOffset = stream.Length - Marker.Length - sizeof(long) - payloadLength;
            if (payloadLength <= 0 || payloadLength > int.MaxValue || payloadOffset <= 0)
            {
                throw new InvalidOperationException("This executable contains an invalid ByteScry payload length.");
            }

            stream.Seek(payloadOffset, SeekOrigin.Begin);
            byte[] payload = new byte[(int)payloadLength];
            ReadFully(stream, payload);
            return payload;
        }
    }

    private static void ReadFully(Stream stream, byte[] buffer)
    {
        int offset = 0;
        while (offset < buffer.Length)
        {
            int read = stream.Read(buffer, offset, buffer.Length - offset);
            if (read == 0)
            {
                throw new EndOfStreamException();
            }
            offset += read;
        }
    }

    private static string ComputeHash(byte[] payload)
    {
        using (SHA256 sha256 = SHA256.Create())
        {
            return BitConverter.ToString(sha256.ComputeHash(payload)).Replace("-", "").ToLowerInvariant();
        }
    }

    private static string FindExtractedApp(string extractRoot)
    {
        if (!Directory.Exists(extractRoot))
        {
            return null;
        }

        return Directory.GetFiles(extractRoot, "bytescry.exe", SearchOption.AllDirectories)
            .FirstOrDefault(path => path.IndexOf("\\bin\\", StringComparison.OrdinalIgnoreCase) >= 0);
    }

    private static string QuoteArguments(string[] args)
    {
        return string.Join(" ", args.Select(QuoteArgument));
    }

    private static string QuoteArgument(string arg)
    {
        if (string.IsNullOrEmpty(arg))
        {
            return "\"\"";
        }
        if (arg.IndexOfAny(new[] { ' ', '\t', '"' }) < 0)
        {
            return arg;
        }
        return "\"" + arg.Replace("\\", "\\\\").Replace("\"", "\\\"") + "\"";
    }
}
