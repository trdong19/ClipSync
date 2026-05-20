package com.clipsync.shizuku;

interface IClipSyncUserService {
    boolean grantClipboardPermission(String packageName);
    boolean checkClipboardPermission(String packageName);
}
