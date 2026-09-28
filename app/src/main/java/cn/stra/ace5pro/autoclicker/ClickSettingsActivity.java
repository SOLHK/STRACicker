package cn.stra.ace5pro.autoclicker;

import android.app.Activity;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Point and timing configuration, kept inside the app rather than the floating controller. */
public final class ClickSettingsActivity extends Activity {
    private SharedPreferences prefs;
    private LinearLayout pointsList;
    private EditText interval, cycles;
    private boolean waitingPermission;
    private boolean receiverRegistered;
    private final BroadcastReceiver pointReceiver = new BroadcastReceiver() {
        @Override public void onReceive(android.content.Context context, Intent intent) { refreshPoints(); }
    };
    private int ink, muted, primary, surface, bg;
    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("stra_ace5pro_clicker", MODE_PRIVATE);
        ink=Color.rgb(30,35,48); muted=Color.rgb(96,105,123); primary=Color.rgb(49,94,184); surface=Color.WHITE; bg=Color.rgb(246,248,252);
        build();
    }
    @Override protected void onResume() {
        super.onResume(); refreshPoints();
        IntentFilter filter = new IntentFilter(OverlayService.ACTION_POINT_ADDED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(pointReceiver, filter, RECEIVER_NOT_EXPORTED);
        else registerReceiver(pointReceiver, filter);
        receiverRegistered = true;
        if (waitingPermission) { waitingPermission=false; if (Settings.canDrawOverlays(this)) pickPoint(); }
    }
    private String validInterval(){try{double d=Double.parseDouble(prefs.getString("interval_ms","0.5"));if(d>=0.5&&d<=2000)return String.valueOf(d);}catch(Exception ignored){}prefs.edit().putString("interval_ms","0.5").apply();return "0.5";}
    private GradientDrawable shape(int color,int r) { GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(r)); return d; }
    private TextView text(String value,float size,int color) { TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); return t; }
    private void build() {
        LinearLayout page=new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(dp(20),dp(28),dp(20),dp(32)); page.setBackgroundColor(bg);
        TextView title=text("点击设置",28,ink); title.setTypeface(null,1); page.addView(title);
        TextView sub=text("点位和点击节奏在这里调整",14,muted); LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2); sp.topMargin=dp(5); sp.bottomMargin=dp(20); page.addView(sub,sp);
        LinearLayout timing=card(); timing.addView(text("点击周期（毫秒）",14,muted));
        interval=input(validInterval(),true); timing.addView(interval,new LinearLayout.LayoutParams(-1,dp(54)));
        LinearLayout chips=new LinearLayout(this); String[] values={"0.5","1","5","10"};
        for(String value:values){TextView chip=button(value+" ms"); LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(42),1); cp.rightMargin=dp(5); chips.addView(chip,cp); chip.setOnClickListener(v->interval.setText(value));} timing.addView(chips);
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2); tp.bottomMargin=dp(12); page.addView(timing,tp);
        LinearLayout rounds=card(); rounds.addView(text("运行轮数（0 表示无限）",14,muted)); cycles=input(String.valueOf(prefs.getLong("cycles",0)),false); rounds.addView(cycles,new LinearLayout.LayoutParams(-1,dp(54)));
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2); rp.bottomMargin=dp(20); page.addView(rounds,rp);
        TextView pts=text("点击点位",18,ink); pts.setTypeface(null,1); page.addView(pts);
        TextView hint=text("添加后可在屏幕上拖动位置；运行时控制器会避开点位。",12,muted); LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2); hp.topMargin=dp(4); hp.bottomMargin=dp(10); page.addView(hint,hp);
        pointsList=new LinearLayout(this); pointsList.setOrientation(LinearLayout.VERTICAL); page.addView(pointsList);
        TextView add=button("＋ 添加点位"); LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(52)); ap.topMargin=dp(8); ap.bottomMargin=dp(22); page.addView(add,ap); add.setOnClickListener(v->requestPick());
        TextView save=button("保存设置"); save.setBackground(shape(primary,25)); save.setTextColor(Color.WHITE); page.addView(save,new LinearLayout.LayoutParams(-1,dp(54))); save.setOnClickListener(v->save());
        ScrollView sc=new ScrollView(this); sc.setFillViewport(true); sc.addView(page); setContentView(sc);
    }
    private LinearLayout card(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(16),dp(14),dp(16),dp(14));l.setBackground(shape(surface,22));return l;}
    private TextView button(String s){TextView t=text(s,15,primary);t.setGravity(Gravity.CENTER);t.setBackground(shape(Color.rgb(230,237,250),22));return t;}
    private EditText input(String value,boolean decimal){EditText e=new EditText(this);e.setSingleLine(true);e.setText(value);e.setTextColor(ink);e.setTextSize(18);e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|(decimal?android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL:0));return e;}
    private void save(){try{double ms=Double.parseDouble(interval.getText().toString().trim());long n=Long.parseLong(cycles.getText().toString().trim());if(!Double.isFinite(ms)||ms<0.5||ms>2000||n<0)throw new Exception();prefs.edit().putString("interval_ms",String.valueOf(ms)).putLong("cycles",n).apply();Toast.makeText(this,"设置已保存",Toast.LENGTH_SHORT).show();}catch(Exception e){Toast.makeText(this,"周期需为 0.5–2000 毫秒，轮数需为非负整数",Toast.LENGTH_LONG).show();}}
    private void requestPick(){if(!Settings.canDrawOverlays(this)){waitingPermission=true;Intent i=new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:"+getPackageName()));startActivity(i);Toast.makeText(this,"首次添加点位需要授权悬浮窗权限",Toast.LENGTH_LONG).show();return;}pickPoint();}
    private void pickPoint(){Intent i=new Intent(this,OverlayService.class);i.setAction(OverlayService.ACTION_PICK_POINT);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);}
    private void refreshPoints(){if(pointsList==null)return;pointsList.removeAllViews();String saved=prefs.getString("points","");if(saved==null||saved.isEmpty()){pointsList.addView(text("尚未添加点位",13,muted));return;}String[] pairs=saved.split(";");for(int index=0;index<pairs.length;index++){final int at=index;LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(4),dp(6),dp(4));row.setBackground(shape(surface,16));TextView pos=text((index+1)+"  ·  "+pairs[index].replace(","," , "),14,ink);row.addView(pos,new LinearLayout.LayoutParams(0,dp(46),1));TextView del=text("删除",13,Color.rgb(179,52,67));del.setGravity(Gravity.CENTER);row.addView(del,new LinearLayout.LayoutParams(dp(52),dp(42)));del.setOnClickListener(v->removePoint(at));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(6);pointsList.addView(row,lp);}}
    private void removePoint(int index){String saved=prefs.getString("points","");java.util.ArrayList<String> list=new java.util.ArrayList<>();if(saved!=null&&!saved.isEmpty())java.util.Collections.addAll(list,saved.split(";"));if(index>=0&&index<list.size())list.remove(index);prefs.edit().putString("points",android.text.TextUtils.join(";",list)).apply();sendReload();refreshPoints();}
    private void sendReload(){Intent i=new Intent(this,OverlayService.class);i.setAction(OverlayService.ACTION_RELOAD_POINTS);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);}
    @Override protected void onPause(){if(receiverRegistered){unregisterReceiver(pointReceiver);receiverRegistered=false;}if(interval!=null&&cycles!=null)saveSilently();super.onPause();}
    private void saveSilently(){try{double ms=Double.parseDouble(interval.getText().toString());long n=Long.parseLong(cycles.getText().toString());if(ms>=0.5&&ms<=2000&&n>=0)prefs.edit().putString("interval_ms",String.valueOf(ms)).putLong("cycles",n).apply();}catch(Exception ignored){}}
}
