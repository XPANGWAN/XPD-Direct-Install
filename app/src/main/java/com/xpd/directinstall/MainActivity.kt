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
  val root=LinearLayout(this).apply{orientation=1;setPadding(20,22,20,20);setBackgroundColor(bg)}
  val scroll=ScrollView(this).apply{clipToPadding=false}
  val body=LinearLayout(this).apply{orientation=1}
  body.addView(label("XPD",30f,Color.WHITE).apply{typeface=android.graphics.Typeface.create("sans-serif-black",android.graphics.Typeface.BOLD);letterSpacing=.10f})
  body.addView(label("DIRECT INSTALL",13f,silver).apply{letterSpacing=.22f;setPadding(2,0,0,4)})
  body.addView(label("PS4 PACKAGE TRANSFER",10f,muted).apply{letterSpacing=.16f;setPadding(2,5,0,22)})
  body.addView(sectionHeader("01  /  CONSOLE CONNECTION"))
  val connection=card()
  ip=field("PS4 IP address",false);connection.addView(label("CONSOLE IP ADDRESS",10f,muted),lp(-1,20));connection.addView(ip,lp(-1,52).apply{bottomMargin=12})
  port=field("12800",true).apply{setText("12800")};connection.addView(label("REMOTE INSTALLER PORT",10f,muted),lp(-1,20));connection.addView(port,lp(-1,52))
  check=button("TEST CONNECTION"){checkReceiver()};connection.addView(check,lp(-1,50).apply{topMargin=14})
  connection.addView(label("Use the port configured by the receiver on your PS4. RPI uses 12800. GoldHEN uses a different payload protocol on 9090.",10f,muted).apply{setPadding(2,10,2,0)})
  body.addView(connection,lp(-1,-2).apply{bottomMargin=22})
  body.addView(sectionHeader("02  /  PACKAGE QUEUE"))
  val packages=card()
  packages.addView(label("SELECT PKG FILES",10f,muted),lp(-1,20))
  packages.addView(button("＋   ADD PACKAGE FILES"){chooseFiles()},lp(-1,52).apply{topMargin=8})
  fileInfo=label("No packages selected",12f,muted).apply{setPadding(2,13,2,8)};packages.addView(fileInfo)
  queue=label("Your selected packages will appear here.",11f,muted).apply{setPadding(2,4,2,4)};packages.addView(queue)
  body.addView(packages,lp(-1,-2).apply{bottomMargin=22})
  body.addView(sectionHeader("03  /  TRANSFER"))
  val transfer=card()
  status=label("READY  •  Add PKG files and test your PS4 connection.",12f,silver).apply{setPadding(2,4,2,12)};transfer.addView(status)
  send=button("SEND TO PS4"){sendFiles()}.apply{isEnabled=false};transfer.addView(send,lp(-1,56))
  serverInfo=label("Phone file server starts automatically on port 8080.",10f,muted).apply{setPadding(2,12,2,2)};transfer.addView(serverInfo)
  body.addView(transfer,lp(-1,-2).apply{bottomMargin=18})
  body.addView(label("Both devices must be connected to the same Wi-Fi network. Keep XPD open while the PS4 downloads the selected files. A successful request means accepted by the receiver, not confirmed installation.",10f,muted).apply{setPadding(4,4,4,22);gravity=Gravity.CENTER})
  scroll.addView(body);root.addView(scroll);setContentView(root)
 }
 private fun sectionHeader(s:String)=label(s,10f,silver).apply{letterSpacing=.14f;setPadding(2,0,2,10)}
 private fun card()=LinearLayout(this).apply{
  orientation=1;setPadding(16,16,16,16)
  background=android.graphics.drawable.GradientDrawable().apply{setColor(panel);cornerRadius=18f;setStroke(1,Color.rgb(48,51,58))}
 }
 private fun label(s:String,n:Float,c:Int)=TextView(this).apply{text=s;textSize=n;setTextColor(c)}
 private fun title(s:String)=label(s,12f,silver).apply{letterSpacing=.08f}
 private fun lp(w:Int,h:Int)=LinearLayout.LayoutParams(w,h)
 private fun field(h:String,n:Boolean)=EditText(this).apply{hint=h;setHintTextColor(muted);setTextColor(Color.WHITE);textSize=14f;setSingleLine(true);setPadding(14,13,14,13);setBackgroundColor(panel);inputType=if(n)android.text.InputType.TYPE_CLASS_NUMBER else android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS}
 private fun button(s:String,primary:Boolean=false,action:()->Unit)=Button(this).apply{text=s;setTextColor(if(primary)Color.BLACK else silver);setBackgroundTintList(android.content.res.ColorStateList.valueOf(if(primary)silver else panel));setOnClickListener{action()}}
 private fun chooseFiles(){val i=Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="*/*";putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);putExtra(Intent.EXTRA_MIME_TYPES,arrayOf("application/vnd.playstation.pkg","application/octet-stream"))};startActivityForResult(i,41)}
 private fun validIp(s:String)=s.matches(Regex("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$"))&&s.split(".").all{it.toIntOrNull()?.let { n -> n in 0..255 } == true}
 private fun checkReceiver(){
  val host=ip.text.toString().trim();val p=port.text.toString().toIntOrNull()
  if(!validIp(host)){status.text="Enter a valid PS4 IPv4 address.";return}
  if(p==null||p !in 1..65535){status.text="Enter a valid port.";return}
  check.isEnabled=false;status.text="Checking PS4 receiver…"
  Thread{
   val result=if(p==9090){
    probeHttp(host,9090,"/status","GoldHEN")
   }else{
    probeHttp(host,p,"/api","RPI")
   }
   runOnUiThread{status.text=result;check.isEnabled=true}
  }.start()
 }
 private fun probeHttp(host:String,p:Int,path:String,kind:String):String{
  var conn:HttpURLConnection?=null
  return try{
   conn=URL("http://$host:$p$path").openConnection() as HttpURLConnection
   conn.requestMethod="GET";conn.connectTimeout=4000;conn.readTimeout=4000
   val code=conn.responseCode
   val body=(if(code in 200..299)conn.inputStream else conn.errorStream)?.bufferedReader()?.use{it.readText()}?:""
   if(kind=="GoldHEN"&&body.replace(" ","").contains("\\"status\\":\\"ready\\""))
    "GoldHEN detected on 9090. Online; this build does not yet include the GoldHEN installer payload."
   else if(kind=="RPI"&&(body.contains("Unsupported method",true)||body.contains("\\"fail\\"")||code in 200..299))
    "RPI detected on port $p."
   else "Port $p responded (HTTP $code), but $kind was not confirmed."
  }catch(e:Exception){"No $kind response on $p: "+(e.message?:"connection failed")}finally{conn?.disconnect()}
 }
 private fun sendFiles(){
  if(busy)return;if(files.isEmpty()){status.text="Add at least one .pkg file.";return}
  val host=ip.text.toString().trim();val p=port.text.toString().toIntOrNull()
  if(!validIp(host)){status.text="Enter a valid PS4 IPv4 address.";return};if(p==null||p !in 1..65535){status.text="Enter a valid receiver port.";return}
  if(p==9090){status.text="GoldHEN uses payload injection, not the RPI /api/install request. This build detects GoldHEN but does not yet send PKG through it.";return}
  if(p!=12800){status.text="RPI installation is supported on port 12800 in this build.";return}
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
 private fun localIp():String? {
  return try { val all=NetworkInterface.getNetworkInterfaces() ?: return null; for(n in Collections.list(all)) if(n.isUp && !n.isLoopback) for(a in Collections.list(n.inetAddresses)) if(a is Inet4Address && a.isSiteLocalAddress && !a.isLoopbackAddress) return a.hostAddress; null } catch(_:Exception) { null }
 }
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
