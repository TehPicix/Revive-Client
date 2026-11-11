package revivedevelopment.reviveclient.utils.other;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.Tlhelp32.PROCESSENTRY32;
import com.sun.jna.platform.win32.WinDef.DWORD;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary.StdCallCallback;
import java.util.ArrayList;
import java.util.List;

public class SpotifyGrabber {
   private static final int PROCESS_QUERY_LIMITED_INFORMATION = 4096;
   private static final int TH32CS_SNAPPROCESS = 2;

   public static List<Integer> getSpotifyPIDs() {
      List<Integer> spotifyPIDs = new ArrayList<>();
      HANDLE snapshot = SpotifyGrabber.Kernel32Ext.INSTANCE.CreateToolhelp32Snapshot(2, 0);
      if (snapshot != null && !WinNT.INVALID_HANDLE_VALUE.equals(snapshot)) {
         PROCESSENTRY32 processEntry = new PROCESSENTRY32();
         processEntry.dwSize = new DWORD(processEntry.size());
         if (SpotifyGrabber.Kernel32Ext.INSTANCE.Process32First(snapshot, processEntry)) {
            do {
               int pid = processEntry.th32ProcessID.intValue();
               String processPath = getProcessPath(pid);
               if (processPath.toLowerCase().contains("spotify.exe")) {
                  spotifyPIDs.add(pid);
               }
            } while (SpotifyGrabber.Kernel32Ext.INSTANCE.Process32Next(snapshot, processEntry));
         }

         SpotifyGrabber.Kernel32Ext.INSTANCE.CloseHandle(snapshot);
         if (spotifyPIDs.isEmpty()) {
         }

         return spotifyPIDs;
      } else {
         System.out.println("[ERROR] Failed to create process snapshot.");
         return spotifyPIDs;
      }
   }

   private static String getProcessPath(int pid) {
      HANDLE hProcess = SpotifyGrabber.Kernel32Ext.INSTANCE.OpenProcess(4096, false, pid);
      if (hProcess != null && !WinNT.INVALID_HANDLE_VALUE.equals(hProcess)) {
         char[] buffer = new char[1024];
         IntByReference size = new IntByReference(buffer.length);
         boolean success = SpotifyGrabber.Kernel32Ext.INSTANCE.QueryFullProcessImageNameW(hProcess, 0, buffer, size);
         SpotifyGrabber.Kernel32Ext.INSTANCE.CloseHandle(hProcess);
         return !success ? "Unknown" : new String(buffer, 0, size.getValue()).trim();
      } else {
         return "Unknown";
      }
   }

   public static String getSpotifySongTitle() {
      List<Integer> spotifyPIDs = getSpotifyPIDs();
      if (spotifyPIDs.isEmpty()) {
         return "Not Playing";
      } else {
         char[] windowText = new char[512];
         HWND[] bestMatchHwnd = new HWND[1];
         SpotifyGrabber.User32Ext.INSTANCE.EnumWindows((hWnd, data) -> {
            IntByReference pidRef = new IntByReference();
            SpotifyGrabber.User32Ext.INSTANCE.GetWindowThreadProcessId(hWnd, pidRef);
            int windowPID = pidRef.getValue();
            if (spotifyPIDs.contains(windowPID)) {
               SpotifyGrabber.User32Ext.INSTANCE.GetWindowTextW(hWnd, windowText, 512);
               String title = Native.toString(windowText).trim();
               if (title.contains(" - ")) {
                  bestMatchHwnd[0] = hWnd;
                  return false;
               }
            }

            return true;
         }, null);
         if (bestMatchHwnd[0] != null) {
            SpotifyGrabber.User32Ext.INSTANCE.GetWindowTextW(bestMatchHwnd[0], windowText, 512);
            String fullTitle = Native.toString(windowText).trim();
            if (fullTitle.endsWith(" - Spotify")) {
               fullTitle = fullTitle.substring(0, fullTitle.length() - 10).trim();
            }

            return fullTitle.isEmpty() ? "Not Playing" : fullTitle;
         } else {
            return "Not Playing";
         }
      }
   }

   public interface Kernel32Ext extends Kernel32 {
      SpotifyGrabber.Kernel32Ext INSTANCE = (SpotifyGrabber.Kernel32Ext)Native.load("kernel32", SpotifyGrabber.Kernel32Ext.class);

      HANDLE OpenProcess(int var1, boolean var2, int var3);

      boolean QueryFullProcessImageNameW(HANDLE var1, int var2, char[] var3, IntByReference var4);

      boolean CloseHandle(HANDLE var1);

      HANDLE CreateToolhelp32Snapshot(int var1, int var2);
   }

   public interface User32Ext extends User32 {
      SpotifyGrabber.User32Ext INSTANCE = (SpotifyGrabber.User32Ext)Native.load("user32", SpotifyGrabber.User32Ext.class);

      int GetWindowTextW(HWND var1, char[] var2, int var3);

      int GetWindowThreadProcessId(HWND var1, IntByReference var2);

      boolean EnumWindows(com.sun.jna.platform.win32.WinUser.WNDENUMPROC var1, Pointer var2);
   }

   public interface WNDENUMPROC extends StdCallCallback {
      boolean callback(HWND var1, Pointer var2);
   }
}
