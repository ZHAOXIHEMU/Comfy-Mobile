package com.comfymobile.app;

/** Activity-to-activity navigation is one foreground session. */
final class ForegroundSession {
 private int activities;
 private boolean foreground;
 boolean start(){activities++;if(foreground)return false;foreground=true;return true;}
 void stop(boolean changingConfiguration){activities=Math.max(0,activities-1);if(activities==0&&!changingConfiguration)foreground=false;}
}
