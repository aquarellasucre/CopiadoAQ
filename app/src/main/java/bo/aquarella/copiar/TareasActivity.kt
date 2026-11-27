package bo.aquarella.copiar

import android.Manifest
import android.app.* 
import android.content.* 
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.*
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.* 

class TareasActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("tareas_diarias", MODE_PRIVATE) }
    private lateinit var list: LinearLayout
    private val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        solicitarPermisosNotificacion()
        crearCanal()
        render()
    }

    private fun render() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(22,22,22,22) }
        root.addView(TextView(this).apply { text="Pendientes de hoy"; textSize=24f; setTypeface(null, Typeface.BOLD) })
        root.addView(TextView(this).apply { text=SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date()); setPadding(0,4,0,12) })
        root.addView(Button(this).apply { text="+ Agregar tarea"; isAllCaps=false; setOnClickListener { editor(null) } })
        list=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
        refresh()
    }

    private fun items(): JSONArray = try { JSONArray(prefs.getString("items","[]")) } catch (_:Exception) { JSONArray() }
    private fun saveItems(a:JSONArray) { prefs.edit().putString("items",a.toString()).apply() }

    private fun refresh() {
        list.removeAllViews()
        val a=items(); var count=0
        for(i in 0 until a.length()) {
            val o=a.optJSONObject(i) ?: continue
            if(o.optString("day")!=today) continue
            count++
            val row=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(12,12,12,12) }
            val top=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=android.view.Gravity.CENTER_VERTICAL }
            val check=CheckBox(this).apply { text=o.optString("title"); isChecked=o.optBoolean("done"); textSize=17f; setOnCheckedChangeListener { _,v -> o.put("done",v); saveItems(a); if(v) cancelAlarm(o.optString("id")) else scheduleAlarm(o); refresh() } }
            top.addView(check,LinearLayout.LayoutParams(0,-2,1f))
            val edit=Button(this).apply { text="Editar"; isAllCaps=false; setOnClickListener { editor(o.optString("id")) } }
            val del=Button(this).apply { text="×"; setOnClickListener { a.remove(i); saveItems(a); cancelAlarm(o.optString("id")); refresh() } }
            top.addView(edit); top.addView(del); row.addView(top)
            val time=o.optString("time")
            if(time.isNotBlank()) row.addView(TextView(this).apply { text="⏰ $time"; textSize=14f; setPadding(44,0,0,0) })
            val note=o.optString("note")
            if(note.isNotBlank()) row.addView(TextView(this).apply { text=note; setPadding(44,4,0,0) })
            list.addView(row); list.addView(View(this).apply { setBackgroundColor(0xFFE2E2E2.toInt()) },LinearLayout.LayoutParams(-1,1))
        }
        if(count==0) list.addView(TextView(this).apply { text="No tienes tareas para hoy.\nAgrega una tarea para comenzar."; textSize=16f; setPadding(8,24,8,24) })
    }

    private fun editor(id:String?) {
        val a=items(); var existing:JSONObject?=null
        for(i in 0 until a.length()) if(a.optJSONObject(i)?.optString("id")==id) existing=a.optJSONObject(i)
        val title=EditText(this).apply { hint="Tarea (obligatorio)"; setText(existing?.optString("title") ?: "") }
        val note=EditText(this).apply { hint="Nota (opcional)"; setText(existing?.optString("note") ?: "") }
        val timeButton=Button(this).apply { isAllCaps=false; text=existing?.optString("time")?.takeIf{it.isNotBlank()}?.let{"Hora: $it"} ?: "Agregar alarma (opcional)" }
        var selectedTime=existing?.optString("time")?.takeIf{it.isNotBlank()}
        timeButton.setOnClickListener { TimePickerDialog(this,{_,h,m -> selectedTime=String.format(Locale.getDefault(),"%02d:%02d",h,m); timeButton.text="Hora: $selectedTime" },selectedTime?.substringBefore(":")?.toIntOrNull()?:8,selectedTime?.substringAfter(":")?.toIntOrNull()?:0,true).show() }
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(24,8,24,8); addView(title); addView(note); addView(timeButton) }
        AlertDialog.Builder(this).setTitle(if(id==null) "Nueva tarea" else "Editar tarea").setView(box)
            .setPositiveButton("Guardar"){_,_->
                if(title.text.toString().isBlank()){ Toast.makeText(this,"Escribe el nombre de la tarea",Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                val obj=existing ?: JSONObject().put("id",UUID.randomUUID().toString()).put("day",today).put("done",false)
                obj.put("title",title.text.toString().trim()).put("note",note.text.toString().trim()).put("time",selectedTime ?: "").put("day",today)
                if(existing==null) a.put(obj)
                saveItems(a); scheduleAlarm(obj); refresh()
                if (selectedTime != null && Build.VERSION.SDK_INT >= 31) {
                    val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
                    if (!alarmManager.canScheduleExactAlarms()) {
                        AlertDialog.Builder(this)
                            .setTitle("Permitir alarmas exactas")
                            .setMessage("Para que Copiar AQ avise exactamente a la hora programada, habilita el permiso de alarmas y recordatorios.")
                            .setPositiveButton("Configurar") { _, _ ->
                                try { startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:$packageName"))) } catch (_:Exception) { }
                            }
                            .setNegativeButton("Continuar sin permiso", null)
                            .show()
                    }
                }
            }.setNegativeButton("Cancelar",null).show()
    }

    private fun scheduleAlarm(o:JSONObject) {
        cancelAlarm(o.optString("id")); val time=o.optString("time"); if(time.isBlank() || o.optBoolean("done")) return
        val parts=time.split(":"); if(parts.size!=2) return
        val cal=Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY,parts[0].toInt()); set(Calendar.MINUTE,parts[1].toInt()); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0) }
        if(cal.timeInMillis<=System.currentTimeMillis()) return
        val intent=Intent(this,TareaAlarmReceiver::class.java).putExtra("id",o.optString("id")).putExtra("title",o.optString("title")).putExtra("note",o.optString("note"))
        val pi=PendingIntent.getBroadcast(this,o.optString("id").hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val am=getSystemService(ALARM_SERVICE) as AlarmManager
        if(Build.VERSION.SDK_INT>=31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,cal.timeInMillis,pi)
        } else if(Build.VERSION.SDK_INT>=23) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,cal.timeInMillis,pi) else am.setExact(AlarmManager.RTC_WAKEUP,cal.timeInMillis,pi)
    }
    private fun cancelAlarm(id:String) { val intent=Intent(this,TareaAlarmReceiver::class.java); val pi=PendingIntent.getBroadcast(this,id.hashCode(),intent,PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) ?: return; (getSystemService(ALARM_SERVICE) as AlarmManager).cancel(pi); pi.cancel() }
    private fun solicitarPermisosNotificacion(){ if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),12) }
    private fun crearCanal(){ if(Build.VERSION.SDK_INT>=26){ val ch=NotificationChannel(TareaAlarmReceiver.CHANNEL,"Alarmas de tareas",NotificationManager.IMPORTANCE_HIGH).apply { description="Avisos con sonido para tareas programadas"; setSound(android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI,android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ALARM).build()); enableVibration(true) }; (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch) } }
}

class TareaAlarmReceiver: BroadcastReceiver() {
    companion object { const val CHANNEL="tareas_alarmas" }
    override fun onReceive(context:Context,intent:Intent) {
        val title=intent.getStringExtra("title") ?: "Tarea pendiente"
        val note=intent.getStringExtra("note") ?: ""
        val open=PendingIntent.getActivity(context,0,Intent(context,TareasActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n=NotificationCompat.Builder(context,CHANNEL).setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("⏰ $title").setContentText(note.ifBlank { "Es hora de realizar tu tarea" }).setStyle(NotificationCompat.BigTextStyle().bigText(note.ifBlank { "Es hora de realizar tu tarea" })).setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_ALARM).setSound(android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI).setVibrate(longArrayOf(0,500,250,500,250,800)).setAutoCancel(true).setContentIntent(open).build()
        try { NotificationManagerCompat.from(context).notify(intent.getStringExtra("id")?.hashCode() ?: System.currentTimeMillis().toInt(),n) } catch (_:SecurityException) { }
    }
}
