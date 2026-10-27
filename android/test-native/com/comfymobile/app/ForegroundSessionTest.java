package com.comfymobile.app;
public final class ForegroundSessionTest {
 static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
 public static void main(String[] args){ForegroundSession session=new ForegroundSession();int reads=0;if(session.start())reads++;if(session.start())reads++;session.stop(false);session.stop(false);if(session.start())reads++;check(reads==2,"cold launch and real foreground return refresh");if(session.start())reads++;session.stop(false);if(session.start())reads++;session.stop(false);check(reads==2,"main/picker navigation and reopening picker do not requery");session.stop(true);if(session.start())reads++;check(reads==2,"configuration recreation does not refresh");session.stop(false);if(session.start())reads++;check(reads==3,"next real background return refreshes again");System.out.println("PASS: photo-index refresh only on foreground sessions; internal activity transitions and configuration changes reuse the index");}
}
