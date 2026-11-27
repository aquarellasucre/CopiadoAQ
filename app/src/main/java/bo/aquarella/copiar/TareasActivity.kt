package bo.aquarella.copiar

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.* 

class TareasActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("tareas_diarias", MODE_PRIVATE) }
    private lateinit var list: LinearLayout
    private val todayDate = LocalDate.now()
    private val today = todayDate.toString()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormatter = DateTimeFormatter.ofPattern("d 'de' MMMM", Locale("es", "BO"))
    private val bgColor = Color.rgb(246, 247, 249)
    private val textColor = Color.rgb(31, 41, 55)
    private val secondaryColor = Color.rgb(105, 112, 122)
    private val overdueColor = Color.rgb(160, 89, 16)
    private val overdueBg = Color.rgb(255, 239, 211)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Tema claro: barra de estado blanca con iconos oscuros, como Consulta Stock.
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_VISIBLE or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.BLACK
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) window.setDecorFitsSystemWindows(true)

        solicitarPermisosNotificacion()
        crearCanal()
        render()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(12))
            setBackgroundColor(bgColor)
        }
        root.addView(TextView(this).apply {
            text = "Tareas de hoy"
            textSize = 24f
            setTextColor(textColor)
            setTypeface(null, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = todayDate.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale("es", "BO")))
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("es", "BO")) else it.toString() }
            textSize = 14f
            setTextColor(secondaryColor)
            setPadding(0, dp(3), 0, dp(12))
        })
        root.addView(Button(this).apply {
            text = "+ Agregar tarea"
            isAllCaps = false
            setOnClickListener { editor(null) }
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply {
            isFillViewport = false
            addView(list)
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        refresh()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun items(): JSONArray = try { JSONArray(prefs.getString("items", "[]")) } catch (_: Exception) { JSONArray() }
    private fun saveItems(a: JSONArray) { prefs.edit().putString("items", a.toString()).apply() }
    private fun parseDay(day: String): LocalDate? = try { LocalDate.parse(day) } catch (_: Exception) { null }

    private fun refresh() {
        list.removeAllViews()
        val a = items()
        val visible = mutableListOf<Pair<Int, JSONObject>>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val taskDay = parseDay(o.optString("day")) ?: todayDate
            // Las tareas pendientes de días anteriores se arrastran. Las tareas futuras no aparecen.
            // Las completadas de días anteriores se conservan en los datos, pero no saturan la vista.
            if (taskDay.isAfter(todayDate)) continue
            if (taskDay.isBefore(todayDate) && o.optBoolean("done")) continue
            visible.add(i to o)
        }
        visible.sortWith(compareBy<Pair<Int, JSONObject>> { parseDay(it.second.optString("day")) ?: todayDate }.thenBy { it.first })

        val overdueCount = visible.count { parseDay(it.second.optString("day"))?.isBefore(todayDate) == true }
        if (overdueCount > 0) {
            list.addView(TextView(this).apply {
                text = "⚠  $overdueCount ${if (overdueCount == 1) "tarea atrasada" else "tareas atrasadas"} de días anteriores"
                textSize = 14f
                setTextColor(overdueColor)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = rounded(overdueBg, 12)
            })
            addSpacer(8)
        }

        if (visible.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "No tienes tareas para hoy.\nAgrega una tarea para comenzar."
                textSize = 16f
                setTextColor(secondaryColor)
                setPadding(dp(8), dp(24), dp(8), dp(24))
            })
            return
        }

        visible.forEach { (index, o) ->
            val taskDay = parseDay(o.optString("day")) ?: todayDate
            val daysLate = ChronoUnit.DAYS.between(taskDay, todayDate).toInt().coerceAtLeast(0)
            val isOverdue = daysLate > 0
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = rounded(Color.WHITE, 14)
            }
            val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
            val check = CheckBox(this).apply {
                text = o.optString("title")
                isChecked = o.optBoolean("done")
                textSize = 16f
                setTextColor(textColor)
                setPadding(0, 0, dp(4), 0)
                setOnCheckedChangeListener { _, checked ->
                    o.put("done", checked)
                    saveItems(a)
                    if (checked) cancelAlarm(o.optString("id")) else scheduleAlarm(o)
                    refresh()
                }
            }
            top.addView(check, LinearLayout.LayoutParams(0, -2, 1f))
            val edit = Button(this).apply { text = "Editar"; isAllCaps = false; setOnClickListener { editor(o.optString("id")) } }
            val del = Button(this).apply { text = "×"; setOnClickListener { a.remove(index); saveItems(a); cancelAlarm(o.optString("id")); refresh() } }
            top.addView(edit)
            top.addView(del)
            card.addView(top)

            if (isOverdue) {
                val badge = TextView(this).apply {
                    text = "⚠ Atrasada · $daysLate ${if (daysLate == 1) "día" else "días"}"
                    textSize = 12f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(overdueColor)
                    setPadding(dp(9), dp(5), dp(9), dp(5))
                    background = rounded(overdueBg, 9)
                }
                card.addView(badge, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(5); leftMargin = dp(44) })
                card.addView(TextView(this).apply {
                    text = "Programada: ${taskDay.format(dateFormatter)}"
                    textSize = 12f
                    setTextColor(secondaryColor)
                    setPadding(dp(44), dp(4), 0, 0)
                })
            }
            val time = o.optString("time")
            if (time.isNotBlank()) card.addView(TextView(this).apply {
                text = "⏰ $time"
                textSize = 13f
                setTextColor(secondaryColor)
                setPadding(dp(44), dp(4), 0, 0)
            })
            val note = o.optString("note")
            if (note.isNotBlank()) card.addView(TextView(this).apply {
                text = note
                textSize = 14f
                setTextColor(secondaryColor)
                setPadding(dp(44), dp(4), 0, 0)
            })
            list.addView(card)
            addSpacer(8)
        }
    }

    private fun addSpacer(height: Int) {
        list.addView(View(this), LinearLayout.LayoutParams(-1, dp(height)))
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun editor(id: String?) {
        val a = items(); var existing: JSONObject? = null
        for (i in 0 until a.length()) if (a.optJSONObject(i)?.optString("id") == id) existing = a.optJSONObject(i)
        val title = EditText(this).apply { hint = "Tarea (obligatorio)"; setText(existing?.optString("title") ?: "") }
        val note = EditText(this).apply { hint = "Nota (opcional)"; setText(existing?.optString("note") ?: "") }
        val timeButton = Button(this).apply {
            isAllCaps = false
            text = existing?.optString("time")?.takeIf { it.isNotBlank() }?.let { "Hora: $it" } ?: "Agregar alarma (opcional)"
        }
        var selectedTime = existing?.optString("time")?.takeIf { it.isNotBlank() }
        timeButton.setOnClickListener {
            TimePickerDialog(this, { _, h, m -> selectedTime = String.format(Locale.getDefault(), "%02d:%02d", h, m); timeButton.text = "Hora: $selectedTime" },
                selectedTime?.substringBefore(":")?.toIntOrNull() ?: 8,
                selectedTime?.substringAfter(":")?.toIntOrNull() ?: 0, true).show()
        }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(8), dp(24), dp(8)); addView(title); addView(note); addView(timeButton) }
        AlertDialog.Builder(this).setTitle(if (id == null) "Nueva tarea" else "Editar tarea").setView(box)
            .setPositiveButton("Guardar") { _, _ ->
                if (title.text.toString().isBlank()) { Toast.makeText(this, "Escribe el nombre de la tarea", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                // Al editar una tarea atrasada se conserva su fecha original: no se "resetea" el atraso.
                val obj = existing ?: JSONObject().put("id", UUID.randomUUID().toString()).put("day", today).put("done", false)
                obj.put("title", title.text.toString().trim()).put("note", note.text.toString().trim()).put("time", selectedTime ?: "")
                if (existing == null) a.put(obj)
                saveItems(a); scheduleAlarm(obj); refresh()
                if (selectedTime != null && Build.VERSION.SDK_INT >= 31) {
                    val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
                    if (!alarmManager.canScheduleExactAlarms()) {
                        AlertDialog.Builder(this)
                            .setTitle("Permitir alarmas exactas")
                            .setMessage("Para que Copiar AQ avise exactamente a la hora programada, habilita el permiso de alarmas y recordatorios.")
                            .setPositiveButton("Configurar") { _, _ ->
                                try { startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:$packageName"))) } catch (_: Exception) { }
                            }
                            .setNegativeButton("Continuar sin permiso", null)
                            .show()
                    }
                }
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun scheduleAlarm(o: JSONObject) {
        cancelAlarm(o.optString("id")); val time = o.optString("time"); if (time.isBlank() || o.optBoolean("done")) return
        val parts = time.split(":"); if (parts.size != 2) return
        val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, parts[0].toInt()); set(Calendar.MINUTE, parts[1].toInt()); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        if (cal.timeInMillis <= System.currentTimeMillis()) return
        val intent = Intent(this, TareaAlarmReceiver::class.java).putExtra("id", o.optString("id")).putExtra("title", o.optString("title")).putExtra("note", o.optString("note"))
        val pi = PendingIntent.getBroadcast(this, o.optString("id").hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val am = getSystemService(ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        else if (Build.VERSION.SDK_INT >= 23) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi) else am.setExact(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
    }
    private fun cancelAlarm(id: String) {
        val intent = Intent(this, TareaAlarmReceiver::class.java)
        val pi = PendingIntent.getBroadcast(this, id.hashCode(), intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) ?: return
        (getSystemService(ALARM_SERVICE) as AlarmManager).cancel(pi); pi.cancel()
    }
    private fun solicitarPermisosNotificacion() { if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 12) }
    private fun crearCanal() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(TareaAlarmReceiver.CHANNEL, "Alarmas de tareas", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Avisos con sonido para tareas programadas"
                setSound(android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI, android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ALARM).build())
                enableVibration(true)
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }
}

class TareaAlarmReceiver : BroadcastReceiver() {
    companion object { const val CHANNEL = "tareas_alarmas" }
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "Tarea pendiente"
        val note = intent.getStringExtra("note") ?: ""
        val open = PendingIntent.getActivity(context, 0, Intent(context, TareasActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("⏰ $title")
            .setContentText(note.ifBlank { "Es hora de realizar tu tarea" }).setStyle(NotificationCompat.BigTextStyle().bigText(note.ifBlank { "Es hora de realizar tu tarea" }))
            .setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_ALARM).setSound(android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI)
            .setVibrate(longArrayOf(0, 500, 250, 500, 250, 800)).setAutoCancel(true).setContentIntent(open).build()
        try { NotificationManagerCompat.from(context).notify(intent.getStringExtra("id")?.hashCode() ?: System.currentTimeMillis().toInt(), n) } catch (_: SecurityException) { }
    }
}
