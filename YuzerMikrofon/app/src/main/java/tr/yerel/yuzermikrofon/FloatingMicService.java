package tr.yerel.yuzermikrofon;
import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

public class FloatingMicService extends AccessibilityService {
 static FloatingMicService active;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private WindowManager wm;
 private WindowManager.LayoutParams lp;
 private TextView bubble;
 private SpeechRecognizer recognizer;
 private boolean listening=false;
 private AccessibilityNodeInfo lastEditor;
 @Override protected void onServiceConnected(){super.onServiceConnected();active=this;wm=(WindowManager)getSystemService(Context.WINDOW_SERVICE);refresh();}
 @Override public void onAccessibilityEvent(AccessibilityEvent e){
  if(e==null)return;
  AccessibilityNodeInfo n=e.getSource();
  if(n!=null&&n.isEditable()&&!n.isPassword())lastEditor=n;
  handler.removeCallbacks(check);handler.postDelayed(check,140);
 }
 private final Runnable check=this::refresh;
 void refresh(){
  boolean show=getSharedPreferences("p",0).getBoolean("always",false);
  try{List<AccessibilityWindowInfo> wins=getWindows();if(wins!=null)for(AccessibilityWindowInfo w:wins)if(w.getType()==AccessibilityWindowInfo.TYPE_INPUT_METHOD)show=true;}catch(Exception ignored){}
  AccessibilityNodeInfo ed=editor();if(ed!=null&&ed.isPassword())show=false;
  if(show)showBubble();else {if(listening)stop();removeBubble();}
 }
 private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+0.5f);}
 private GradientDrawable color(int c){GradientDrawable d=new GradientDrawable();d.setShape(GradientDrawable.OVAL);d.setColor(c);d.setStroke(dp(2),Color.WHITE);return d;}
 private void showBubble(){
  if(bubble!=null)return;
  bubble=new TextView(this);bubble.setText("🎙");bubble.setTextSize(28);bubble.setGravity(Gravity.CENTER);bubble.setBackground(color(0xff167553));bubble.setContentDescription("Konuşmayı başlat");
  int w=getResources().getDisplayMetrics().widthPixels,h=getResources().getDisplayMetrics().heightPixels;
  lp=new WindowManager.LayoutParams(dp(68),dp(68),WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
  lp.gravity=Gravity.TOP|Gravity.LEFT;
  lp.x=getSharedPreferences("p",0).getInt("x",Math.max(0,w-dp(80)));
  lp.y=getSharedPreferences("p",0).getInt("y",h/2);
  bubble.setOnTouchListener(new android.view.View.OnTouchListener(){
   float sx,sy;int ox,oy;boolean moved;
   public boolean onTouch(android.view.View v,MotionEvent e){
    switch(e.getActionMasked()){
     case MotionEvent.ACTION_DOWN:sx=e.getRawX();sy=e.getRawY();ox=lp.x;oy=lp.y;moved=false;return true;
     case MotionEvent.ACTION_MOVE:
      int dx=(int)(e.getRawX()-sx),dy=(int)(e.getRawY()-sy);
      if(Math.abs(dx)>dp(9)||Math.abs(dy)>dp(9))moved=true;
      if(moved){lp.x=Math.max(0,Math.min(w-dp(68),ox+dx));lp.y=Math.max(0,Math.min(h-dp(68),oy+dy));try{wm.updateViewLayout(bubble,lp);}catch(Exception ignored){}}return true;
     case MotionEvent.ACTION_UP:
      if(moved)getSharedPreferences("p",0).edit().putInt("x",lp.x).putInt("y",lp.y).apply();
      else toggle();return true;
    }return true;
   }
  });
  try{wm.addView(bubble,lp);}catch(Exception ex){bubble=null;}
 }
 private void removeBubble(){if(bubble!=null){try{wm.removeView(bubble);}catch(Exception ignored){}bubble=null;}}
 private AccessibilityNodeInfo editor(){
  try{List<AccessibilityWindowInfo> wins=getWindows();if(wins!=null)for(AccessibilityWindowInfo w:wins){
   if(w.getType()!=AccessibilityWindowInfo.TYPE_APPLICATION)continue;
   AccessibilityNodeInfo root=w.getRoot();if(root==null)continue;
   AccessibilityNodeInfo n=root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
   if(n!=null&&n.isEditable())return n;
  }}catch(Exception ignored){}
  try{if(lastEditor!=null&&lastEditor.refresh()&&lastEditor.isEditable())return lastEditor;}catch(Exception ignored){}
  return null;
 }
 private void visual(boolean on){if(bubble!=null){bubble.setBackground(color(on?0xffb5253a:0xff167553));bubble.setText(on?"●":"🎙");}}
 private void toggle(){
  if(listening){if(recognizer!=null)recognizer.stopListening();listening=false;visual(false);return;}
  if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){msg("Önce mikrofon iznini verin");return;}
  AccessibilityNodeInfo n=editor();if(n==null||n.isPassword()){msg("Önce normal bir yazı alanına dokunun");return;}
  if(!SpeechRecognizer.isRecognitionAvailable(this)){msg("Ses tanıma hizmeti bulunamadı");return;}
  if(recognizer==null){recognizer=SpeechRecognizer.createSpeechRecognizer(this);recognizer.setRecognitionListener(new RecognitionListener(){
   public void onReadyForSpeech(Bundle b){}
   public void onBeginningOfSpeech(){}
   public void onRmsChanged(float f){}
   public void onBufferReceived(byte[] b){}
   public void onEndOfSpeech(){}
   public void onError(int e){listening=false;visual(false);if(e!=SpeechRecognizer.ERROR_NO_MATCH&&e!=SpeechRecognizer.ERROR_SPEECH_TIMEOUT)msg("Ses tanıma durdu: "+e);}
   public void onResults(Bundle b){listening=false;visual(false);ArrayList<String> a=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(a!=null&&!a.isEmpty())insert(a.get(0));}
   public void onPartialResults(Bundle b){}
   public void onEvent(int a,Bundle b){}
  });}
  Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
  i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
  i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"tr-TR");
  listening=true;visual(true);
  try{recognizer.startListening(i);}catch(Exception ex){listening=false;visual(false);msg("Mikrofon başlatılamadı");}
 }
 private void insert(String speech){
  AccessibilityNodeInfo n=editor();if(n==null||n.isPassword()){msg("Metin alanına ulaşılamadı");return;}
  try{
   String old=n.getText()==null?"":n.getText().toString();
   int a=n.getTextSelectionStart(),b=n.getTextSelectionEnd();
   if(a<0||b<0||a>old.length()||b>old.length()){a=old.length();b=a;}
   int s=Math.min(a,b),e=Math.max(a,b);
   String pre=old.substring(0,s),post=old.substring(e);
   String add=(pre.length()>0&&!Character.isWhitespace(pre.charAt(pre.length()-1))?" ":"")+speech.trim();
   Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,pre+add+post);
   if(!n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args)){msg("Buraya otomatik yazı eklenemiyor");return;}
   Bundle cursor=new Bundle();cursor.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT,s+add.length());cursor.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT,s+add.length());n.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION,cursor);
  }catch(Exception ex){msg("Metin eklenemedi");}
 }
 private void stop(){listening=false;if(recognizer!=null)try{recognizer.cancel();}catch(Exception ignored){}visual(false);}
 private void msg(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
 @Override public void onInterrupt(){stop();}
 @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);stop();if(recognizer!=null)recognizer.destroy();removeBubble();active=null;super.onDestroy();}
}