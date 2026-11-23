package com.xpd.directinstall
import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.database.Cursor
import android.provider.OpenableColumns
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class MainActivity : Activity() {
 private val silver=Color.rgb(205,210,218); private val muted=Color.rgb(145,151,160)
 private val panel=Color.rgb(27,29,34); private val bg=Color.rgb(12,13,16)
 private val files=mutableListOf<PkgEntry>(); private val served=ConcurrentHashMap<String,Long>()
 private var server:PkgHttpServer?=null; private lateinit var ip:EditText; private lateinit var port:EditText
 private lateinit var status:TextView; private lateinit var fileInfo:TextView; private lateinit var queue:TextView
 private lateinit var send:Button; private lateinit var check:Button; private lateinit var serverInfo:TextView
 private var busy=false
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);window.statusBarColor=bg;window.navigationBarColor=bg;window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv();buildUi()}
 private fun buildUi(){
  val root=LinearLayout(this).apply{orientation=1;setPadding(22,28,22,22);setBackgroundColor(bg)}
  val scroll=ScrollView(this);val body=LinearLayout(this).apply{orientation=1}
  body.addView(label("XPD  /  DIRECT INSTALL",21f,silver).apply{letterSpacing=.12f})
  body.addView(label("ANDROID PKG SENDER",11f,muted).apply{setPadding(0,8,0,26)})
  body.addView(title("PLAYSTATION CONNECTION"))
  ip=field("PS4 IP address",false);body.addView(ip,lp(-1,54).apply{bottomMargin=10})
  port=field("12800",true).apply{setText("12800")};body.addView(port,lp(-1,54))
  check=button("CHECK REMOTE INSTALLER"){checkReceiver()};body.addView(check,lp(-1,52).apply{topMargin=12})
  body.addView(label("GoldHEN and Remote Package Installer must be running on PS4.",11f,muted).apply{setPadding(2,8,0,0)})
  body.addView(title("PACKAGE FILES").apply{setPadding(0,26,0,12)})
  body.addView(button("ADD PKG FILES"){chooseFiles()},lp(-1,54))
  fileInfo=label("No packages selected",12f,muted).apply{setPadding(2,12,0,8)};body.addView(fileInfo)
  send=button("SEND TO PS4",true){sendFiles()}.apply{isEnabled=false};body.addView(send,lp(-1,56).apply{topMargin=12})
  status=label("Ready • Add PKG files and check your PS4.",12f,muted).apply{setPadding(2,18,0,8)};body.addView(status)
  body.addView(title("LOCAL FILE SERVER").apply{setPadding(0,22,0,8)})
  serverInfo=label("Starts automatically when you send packages.",11f,muted);body.addView(serverInfo)
  body.addView(title("TRANSFER QUEUE").apply{setPadding(0,22,0,10)})
  queue=label("Your selected packages will appear here.",12f,muted);body.addView(queue)
  body.addView(label("Keep XPD open and Remote Package Installer in the foreground during setup.",10f,muted).apply{gravity=Gravity.CENTER;setPadding(0,30,0,10)})
  scroll.addView(body);root.addView(scroll);setContentView(root)
 }
 private fun label(s:String,n:Float,c:Int)=TextView(this).apply{text=s;textSize=n;setTextColor(c)}
 private fun title(s:String)=label(s,12f,silver).apply{letterSpacing=.08f}
 private fun lp(w:Int,h:Int)=LinearLayout.LayoutParams(w,h)
 private fun field(h:String,n:Boolean)=EditText(this).apply{hint=h;setHintTextColor(muted);setTextColor(Color.WHITE);textSize=14f;setSingleLine(true);setPadding(14,13,14,13);setBackgroundColor(panel);inputType=if(n)android.text.InputType.TYPE_CLASS_NUMBER else android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS}
 private fun button(s:String,primary:Boolean=false,action:()->Unit)=Button(this).apply{text=s;setTextColor(if(primary)Color.BLACK else silver);setBackgroundTintList(android.content.res.ColorStateList.valueOf(if(primary)silver else panel));setOnClickListener{action()}}
 private fun chooseFiles(){val i=Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="*/*";putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);putExtra(Intent.EXTRA_MIME_TYPES,arrayOf("application/vnd.playstation.pkg","application/octet-stream"))};startActivityForResult(i,41)}
 private fun validIp(s:String)=s.matches(Regex("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$"))&&s.split(".").all{it.toIntOrNull() in 0..255}
 private fun checkReceiver(){
  val host=ip.text.toString().trim();val p=port.text.toString().toIntOrNull()
  if(!validIp(host)){status.text="Enter a valid PS4 IPv4 address.";return};if(p==null||p !in 1..65535){status.text="Enter a valid port.";return}
  check.isEnabled=false;status.text="Checking Remote Package Installer…"
  Thread{var c:HttpURLConnection?=null;val msg=try{
   c=URL("http://$host:$p/api/is_exists").openConnection() as HttpURLConnection;c.requestMethod="POST";c.connectTimeout=5000;c.readTimeout=5000;c.doOutput=true;c.setRequestProperty("Content-Type","application/json")
   c.outputStream.use{it.write(JSONObject().put("title_id","CUSA00000").toString().toByteArray())}
   val code=c.responseCode;val body=(if(code in 200..299)c.inputStream else c.errorStream)?.bufferedReader()?.use{it.readText()}?:""
   if(code in 200..299)"Remote installer API responded (HTTP $code).\n$body" else "PS4 returned HTTP $code. Check receiver app."
  }catch(e:Exception){"Connection failed. Check IP, port, Wi-Fi and receiver app. "+(e.message?:"")}finally{c?.disconnect()}
  runOnUiThread{status.text=msg;check.isEnabled=true}
  }.start()
 }
 private fun sendFiles(){
  if(busy)return;if(files.isEmpty()){status.text="Add at least one .pkg file.";return}
  val host=ip.text.toString().trim();val p=port.text.toString().toIntOrNull()
  if(!validIp(host)){status.text="Enter a valid PS4 IPv4 address.";return};if(p==null||p !in 1..65535){status.text="Enter a valid receiver port.";return}
  val local=localIp()?:run{status.text="Connect phone and PS4 to the same Wi-Fi network.";return}
  val s=PkgHttpServer(contentResolver,8080,files.toList(),onBytes={id,n->served[id]=n;runOnUiThread{refreshQueue()}},onServerError={m->runOnUiThread{status.text=m}})
  if(!s.start()){status.text="Could not start local server on port 8080.";return}
  server?.stop();server=s;window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  serverInfo.text="Phone server: http://$local:8080/";busy=true;send.isEnabled=false;status.text="Submitting package queue to PS4…"
  Thread{var c:HttpURLConnection?=null;val msg=try{
   val links=JSONArray();files.forEach{links.put("http://$local:8080/pkg/${it.id}")}
   val json=JSONObject().put("type","direct").put("packages",links)
   c=URL("http://$host:$p/api/install").openConnection() as HttpURLConnection;c.requestMethod="POST";c.connectTimeout=10000;c.readTimeout=15000;c.doOutput=true;c.setRequestProperty("Content-Type","application/json")
   c.outputStream.use{it.write(json.toString().toByteArray(Charsets.UTF_8))}
   val code=c.responseCode;val body=(if(code in 200..299)c.inputStream else c.errorStream)?.bufferedReader()?.use{it.readText()}?:""
   if(code in 200..299)"PS4 accepted the queue (HTTP $code). Verify actual installation on the console.\n$body" else "PS4 rejected the request (HTTP $code).\n$body"
  }catch(e:Exception){"Could not submit packages: "+(e.message?:"network error")}finally{c?.disconnect()}
  runOnUiThread{status.text=msg;busy=false;send.isEnabled=files.isNotEmpty()}
  }.start()
 }
 private fun localIp():String? {\n  return try { val all=NetworkInterface.getNetworkInterfaces() ?: return null; for(n in Collections.list(all)) if(n.isUp && !n.isLoopback) for(a in Collections.list(n.inetAddresses)) if(a is Inet4Address && a.isSiteLocalAddress && !a.isLoopbackAddress) return a.hostAddress; null } catch(_:Exception) { null }\n }
 private fun refreshQueue(){
  fileInfo.text=if(files.isEmpty())"No packages selected" else "${files.size} package(s) selected"
  queue.text=if(files.isEmpty())"Your selected packages will appear here." else files.mapIndexed{i,f->val n=served[f.id]?:0L;val pct=if(f.size>0)(n*100/f.size).coerceIn(0,100)else 0;"${i+1}. ${f.name}\n   ${sizeText(f.size)} • served: $pct%"}.joinToString("\n\n")
  send.isEnabled=files.isNotEmpty()&&!busy
 }
 private fun sizeText(n:Long):String{if(n<0)return "Size unknown";val mb=n/1048576.0;return if(mb>=1024)String.format(Locale.US,"%.2f GB",mb/1024)else String.format(Locale.US,"%.1f MB",mb)}
 @Deprecated("Legacy result callback")
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data);if(requestCode!=41||resultCode!=RESULT_OK||data==null)return
  val uris=mutableListOf<Uri>();data.clipData?.let{for(i in 0 until it.itemCount)uris.add(it.getItemAt(i).uri)};if(uris.isEmpty())data.data?.let{uris.add(it)}
  var count=0;uris.forEach{u->val name=query(u,OpenableColumns.DISPLAY_NAME)?:"file.pkg";if(!name.lowercase(Locale.ROOT).endsWith(".pkg")||files.any{it.uri==u})return@forEach
   try{contentResolver.takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(_:SecurityException){}
   files.add(PkgEntry(files.size.toString(),u,name,query(u,OpenableColumns.SIZE)?.toLongOrNull()?:-1L));count++}
  status.text=if(count>0)"$count PKG file(s) added." else "No new .pkg files selected.";refreshQueue()
 }
 private fun query(u:Uri,col:String):String?{var c:Cursor?=null;return try{c=contentResolver.query(u,arrayOf(col),null,null,null);if(c!=null&&c.moveToFirst()){val i=c.getColumnIndex(col);if(i>=0&&!c.isNull(i))c.getString(i)else null}else null}catch(_:Exception){null}finally{c?.close()}}
 override fun onDestroy(){server?.stop();window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);super.onDestroy()}
}
