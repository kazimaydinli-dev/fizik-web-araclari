package tr.yerel.yuzermikrofon;
import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
public class MainActivity extends Activity {
 public void onCreate(Bundle b) {
  super.onCreate(b);
  LinearLayout l=new LinearLayout(this);l.setOrientation(1);l.setPadding(25,45,25,20);
  TextView t=new TextView(this);t.setText("🎙 Yüzer Mikrofon\n\nGboard değişmez. Klavye açılınca büyük mikrofon görünür.\n\nMikrofona dokunarak konuşabilirsiniz. Konuşma bitince metin seçili kutuya eklenir. Düğmeyi sürükleyebilirsiniz.\n\nBu ilk sürüm telefonda test edilmemiştir. Bazı uygulamalar metin eklemeyi engelleyebilir.\n\nErişilebilirlik izni ekran bilgilerine geniş erişim sağlar. Uygulama kendi sunucusuna veri yollamaz. Android konuşma tanıma hizmeti çevrimiçi çalışabilir.\n\nŞifre alanlarında kullanılmaz.");t.setTextSize(17);l.addView(t);
  Button m=new Button(this);m.setText("1. Mikrofon izni");l.addView(m);
  m.setOnClickListener(v->{if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},1);});
  Button a=new Button(this);a.setText("2. Erişilebilirliği etkinleştir");l.addView(a);
  a.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
  CheckBox c=new CheckBox(this);c.setText("Test: Düğmeyi klavye kapalıyken de göster");c.setChecked(getSharedPreferences("p",0).getBoolean("always",false));l.addView(c);
  c.setOnCheckedChangeListener((v,on)->{getSharedPreferences("p",0).edit().putBoolean("always",on).apply();if(FloatingMicService.active!=null)FloatingMicService.active.refresh();});
  setContentView(l);
 }
}