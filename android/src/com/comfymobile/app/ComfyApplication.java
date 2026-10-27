package com.comfymobile.app;

import android.app.*;
import android.os.Bundle;

public final class ComfyApplication extends Application implements Application.ActivityLifecycleCallbacks {
 private final ForegroundSession session=new ForegroundSession();
 PhotoLibrary photos;
 @Override public void onCreate(){super.onCreate();photos=new PhotoLibrary(this);registerActivityLifecycleCallbacks(this);}
 @Override public void onActivityStarted(Activity activity){if(session.start())photos.refresh();}
 @Override public void onActivityStopped(Activity activity){session.stop(activity.isChangingConfigurations());}
 @Override public void onActivityCreated(Activity activity,Bundle state){}
 @Override public void onActivityResumed(Activity activity){}
 @Override public void onActivityPaused(Activity activity){}
 @Override public void onActivitySaveInstanceState(Activity activity,Bundle state){}
 @Override public void onActivityDestroyed(Activity activity){}
}
