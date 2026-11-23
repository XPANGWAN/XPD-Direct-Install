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
import androidx.documentfile.provider.DocumentFile
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
 private lateinit var status:TextView; private lateinit var fileInfo:TextView; private lateinit var queue:LinearLayout
 private lateinit var send:Button; private lateinit var check:Button; private lateinit var clear:Button; private lateinit var serverInfo:TextView
 private var busy=false
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);window.statusBarColor=bg;window.navigationBarColor=bg;window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv();buildUi();restoreState()}
 private fun buildUi(){
  val root=LinearLayout(this).apply{orientation=1;setPadding(20,22,20,20);setBackgroundColor(bg)}
  val scroll=ScrollView(this).apply{clipToPadding=false;isFillViewport=true}
  val body=LinearLayout(this).apply{orientation=1}
  val brand=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL}
  val mark=ImageView(this).apply{setImageResource(R.drawable.xpd_wolf_mark);scaleType=ImageView.ScaleType.FIT_CENTER}
  brand.addView(mark,LinearLayout.LayoutParams(dp(66),dp(66)).apply{rightMargin=dp(14)})
  val brandText=LinearLayout(this).apply{orientation=1}
  brandText.addView(label("XPD",30f,Color.WHITE).apply{typeface=android.graphics.Typeface.create("sans-serif-black",android.graphics.Typeface.BOLD);letterSpacing=.10f})
  brandText.addView(label("DIRECT INSTALL",13f,silver).apply{letterSpacing=.22f})
  brandText.addView(label("ANDROID PKG SENDER  •  v0.9.0",10f,muted).apply{letterSpacing=.08f;setPadding(1,5,0,0)})
  brand.addView(brandText,LinearLayout.LayoutParams(0,-2,1f))
  body.addView(brand,lp(-1,dp(82)).apply{bottomMargin=dp(22)})
  body.addView(sectionHeader("01  /  CONSOLE"))
  val connection=card()
  connection.addView(label("PLAYSTATION CONSOLE  •  LOCAL NETWORK",10f,muted).apply{letterSpacing=.10f},lp(-1,22))
  ip=field("192.168.1.XX",false).apply{inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL}
  connection.addView(label("CONSOLE IP ADDRESS",10f,muted),lp(-1,20));connection.addView(ip,lp(-1,52).apply{bottomMargin=12})
  port=field("12800",true).apply{setText("12800")};connection.addView(label("RECEIVER PORT",10f,muted),lp(-1,20));connection.addView(port,lp(-1,52))
  connection.addView(button("FIND CONSOLE ON WI-FI"){discoverConsole()},lp(-1,48).apply{topMargin=12})
  check=button("CHECK CONNECTION"){checkReceiver()};connection.addView(check,lp(-1,50).apply{topMargin=8})
  connection.addView(label("RPI: 12800  /  GoldHEN status: 9090 (detection only)",10f,muted).apply{setPadding(2,12,2,0)})
  body.addView(connection,lp(-1,-2).apply{bottomMargin=dp(22)})
  body.addView(sectionHeader("02  /  YOUR PACKAGES"))
  val packages=card()
  val queueTop=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL}
  queueTop.addView(label("TRANSFER QUEUE",10f,silver).apply{letterSpacing=.12f},LinearLayout.LayoutParams(0,dp(24),1f))
  clear=button("CLEAR"){if(!busy){files.clear();served.clear();refreshQueue();saveState()}}.apply{isEnabled=!busy};queueTop.addView(clear,lp(dp(82),dp(34)))
  packages.addView(queueTop)
  packages.addView(button("＋   ADD PKG FILES",true){chooseFiles()},lp(-1,dp(52)).apply{topMargin=dp(10)})
  packages.addView(button("SCAN PKG FOLDER"){chooseFolder()},lp(-1,dp(46)).apply{topMargin=dp(8)})
  fileInfo=label("No packages selected",12f,muted).apply{setPadding(2,dp(14),2,dp(8))};packages.addView(fileInfo)
  queue=LinearLayout(this).apply{orientation=1};queue.addView(label("Choose one or more PKG files to build your queue.",11f,muted).apply{setPadding(2,dp(8),2,dp(8))});packages.addView(queue)
  body.addView(packages,lp(-1,-2).apply{bottomMargin=dp(22)})
  body.addView(sectionHeader("03  /  SEND TO CONSOLE"))
  val transfer=card()
  status=label("READY  •  Select PKG files to begin.",12f,silver).apply{setPadding(2,dp(4),2,dp(14))};transfer.addView(status)
  send=button("START TRANSFER",true){sendFiles()}.apply{isEnabled=false};transfer.addView(send,lp(-1,dp(56)))
  serverInfo=label("Local phone server  •  Port 9898",10f,muted).apply{setPadding(2,dp(12),2,2)};transfer.addView(serverInfo)
  body.addView(transfer,lp(-1,-2).apply{bottomMargin=dp(18)})
  body.addView(label("Both devices must be connected to the same Wi-Fi network. Keep XPD open while the PS4 downloads the selected files. A successful request means accepted by the receiver, not confirmed installation.",10f,muted).apply{setPadding(4,4,4,22);gravity=Gravity.CENTER})
  scroll.addView(body);root.addView(scroll);setContentView(root)
 }
 private fun prefs()=getSharedPreferences("xpd_direct_install",MODE_PRIVATE)
 private fun restoreState(){
  val savedIp=prefs().getString("console_ip","")?:""
  if(savedIp.isNotEmpty())ip.setText(savedIp)
  val savedPort=prefs().getString("console_port","12800")?:"12800"
  port.setText(savedPort)
  val raw=prefs().getString("pkg_uris","[]")?:"[]"
  try{
   val arr=JSONArray(raw)
   for(i in 0 until arr.length()){
    val u=Uri.parse(arr.getString(i))
    if(files.any{it.uri==u})continue
    try{
     val name=query(u,OpenableColumns.DISPLAY_NAME)?: "package.pkg"
     if(isValidPkg(u)){
      val size=packageSize(u)
      val metadata=PkgMetadataReader.read(contentResolver,u)
      files.add(PkgEntry(java.util.UUID.randomUUID().toString(),u,displayPkgName(name),size,metadata))
     }
    }catch(_:Exception){}
   }
  }catch(_:Exception){}
  refreshQueue()
 }
 private fun saveState(){
  if(!::ip.isInitialized||!::port.isInitialized)return
  val arr=JSONArray()
  files.forEach{arr.put(it.uri.toString())}
  prefs().edit().putString("console_ip",ip.text.toString().trim())
   .putString("console_port",port.text.toString().trim())
   .putString("pkg_uris",arr.toString()).apply()
 }
 override fun onPause(){saveState();super.onPause()}
 private fun sectionHeader(s:String)=label(s,10f,silver).apply{letterSpacing=.14f;setPadding(2,0,2,10)}
 private fun card()=LinearLayout(this).apply{
  orientation=1;setPadding(16,16,16,16)
  background=android.graphics.drawable.GradientDrawable().apply{setColor(panel);cornerRadius=18f;setStroke(1,Color.rgb(48,51,58))}
 }
 private fun label(s:String,n:Float,c:Int)=TextView(this).apply{text=s;textSize=n;setTextColor(c)}
 private fun title(s:String)=label(s,12f,silver).apply{letterSpacing=.08f}
 private fun dp(v:Int)= (v*resources.displayMetrics.density).toInt()
 private fun lp(w:Int,h:Int)=LinearLayout.LayoutParams(w,h)
 private fun field(h:String,n:Boolean)=EditText(this).apply{hint=h;setHintTextColor(muted);setTextColor(Color.WHITE);textSize=14f;setSingleLine(true);setPadding(14,13,14,13);setBackgroundColor(panel);inputType=if(n)android.text.InputType.TYPE_CLASS_NUMBER else android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS}
 private fun button(s:String,primary:Boolean=false,action:()->Unit)=Button(this).apply{text=s;setTextColor(if(primary)Color.BLACK else silver);setBackgroundTintList(android.content.res.ColorStateList.valueOf(if(primary)silver else panel));setOnClickListener{action()}}
 private fun chooseFolder(){
  if(busy)return
  val intent=Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply{
   addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
  }
  startActivityForResult(intent,42)
 }
 private fun scanFolder(treeUri:Uri){
  status.text="Scanning folder for PlayStation .pkg files…"
  Thread{
   val discovered=mutableListOf<PkgEntry>()
   var inspected=0
   fun walk(folder:DocumentFile,depth:Int){
    if(depth>12||discovered.size>=2000)return
    val children=try{folder.listFiles()}catch(_:Exception){emptyArray()}
    for(child in children){
     if(discovered.size>=2000)break
     if(child.isDirectory){walk(child,depth+1)}
     else{
      val name=child.name?:continue
      if(!name.lowercase(Locale.ROOT).endsWith(".pkg"))continue
      inspected++
      if(PkgMetadataReader.hasPkgMagic(contentResolver,child.uri)){
       val meta=PkgMetadataReader.read(contentResolver,child.uri)
       discovered.add(PkgEntry("",child.uri,name,packageSize(child.uri),meta))
      }
     }
    }
   }
   try{
    val root=DocumentFile.fromTreeUri(this,treeUri)
    if(root!=null)walk(root,0)
   }catch(_:Exception){}
   runOnUiThread{
    var added=0
    discovered.forEach{entry->
     if(files.none{it.uri==entry.uri}){
      files.add(entry.copy(id=java.util.UUID.randomUUID().toString()));added++
     }
    }
    refreshQueue();saveState()
    status.text=if(added>0)"Folder scan complete: $added PKG file(s) added from $inspected candidate(s)." else "No new valid .pkg files found in this folder ($inspected candidates checked)."
   }
  }.start()
 }
 private fun chooseFiles(){if(busy)return;val i=Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="*/*";putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true)};startActivityForResult(i,41)}
 private fun validIp(s:String)=s.matches(Regex("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$"))&&s.split(".").all{it.toIntOrNull()?.let { n -> n in 0..255 } == true}
 private fun discoverConsole(){
  status.text="Scanning your local Wi-Fi network…"
  Thread{
   val found=ConsoleDiscovery.scan(this){message->runOnUiThread{status.text=message}}
   runOnUiThread{
    when{
     found.isEmpty()->status.text="No compatible receiver found. Confirm that the PS4/PS5 receiver is running on the same Wi-Fi."
     found.size==1->{ip.setText(found[0].address);port.setText(found[0].port.toString());status.text="Found ${found[0].service} at ${found[0].address}:${found[0].port}. Check the connection before sending."}
     else->{ip.setText(found[0].address);port.setText(found[0].port.toString());status.text="Found ${found.size} possible receivers. Selected ${found[0].address} (${found[0].service}); verify before sending."}
    }
   }
  }.start()
 }
 private fun checkReceiver(){
  val host=ip.text.toString().trim();val p=port.text.toString().toIntOrNull()
  if(!validIp(host)){status.text="Enter a valid PS4 IPv4 address.";return}
  if(p==null||p !in 1..65535){status.text="Enter a valid port.";return}
  check.isEnabled=false;status.text="Checking PS4 receiver…"
  Thread{
   val result=when(p){
    9090->probeHttp(host,9090,"/status","GoldHEN")
    9021,9020->if(probeTcp(host,p))"GoldHEN binloader reachable on port $p. Ready for payload transfer." else "No GoldHEN binloader response on port $p."
    else->probeHttp(host,p,"/api","RPI")
   }
   runOnUiThread{status.text=result;check.isEnabled=true}
  }.start()
 }
 private fun probeTcp(host:String,p:Int):Boolean{return try{java.net.Socket().use{it.connect(java.net.InetSocketAddress(host,p),2500);true}}catch(_:Exception){false}}
 private fun probeHttp(host:String,p:Int,path:String,kind:String):String{
  var conn:HttpURLConnection?=null
  return try{
   conn=URL("http://$host:$p$path").openConnection() as HttpURLConnection
   conn.requestMethod="GET";conn.connectTimeout=4000;conn.readTimeout=4000
   val code=conn.responseCode
   val body=(if(code in 200..299)conn.inputStream else conn.errorStream)?.bufferedReader()?.use{it.readText()}?:""
   if(kind=="GoldHEN"&&body.contains("status")&&body.contains("ready"))
    "GoldHEN detected on 9090. Online; this build does not yet include the GoldHEN installer payload."
   else if(kind=="RPI"&&(body.contains("Unsupported method",true)&&body.contains("fail",true)))
    "RPI detected on port $p."
   else "Port $p responded (HTTP $code), but $kind was not confirmed."
  }catch(e:Exception){"No $kind response on $p: "+(e.message?:"connection failed")}finally{conn?.disconnect()}
 }
 private fun sendFiles(){
  if(busy)return
  if(files.isEmpty()){status.text="Add at least one .pkg file.";return}
  val host=ip.text.toString().trim()
  val p=port.text.toString().toIntOrNull()
  if(!validIp(host)){status.text="Enter a valid PlayStation IPv4 address.";return}
  if(p==null||p !in 1..65535){status.text="Enter a valid receiver port.";return}
  val goldHen=p==9090||p==9021||p==9020
  if(!goldHen&&p!=12800){status.text="Use RPI port 12800 or a GoldHEN binloader port (9090, 9021 or 9020).";return}
  if(files.any{it.metadata?.platform=="PS5"}){status.text="PS5 PKG metadata is supported, but PS5 receiver installation is not enabled in this build.";return}
  if(goldHen&&files.any{it.metadata?.platform!="PS4"}){status.text="GoldHEN installation currently accepts PS4 PKG files only.";return}
  val local=localIp(host)?:run{status.text="Could not find a phone IPv4 address on the same LAN as the console.";return}
  val s=PkgHttpServer(contentResolver,9898,files.toList(),publicHost=local,onBytes={id,n->
   served.compute(id){_,old->((old?:0L)+n).let{v->val size=files.firstOrNull{it.id==id}?.size?:-1L;if(size>0)v.coerceAtMost(size)else v}}
   runOnUiThread{refreshQueue()}
  },onServerError={m->runOnUiThread{status.text=m}})
  server?.stop();server=null
  if(!s.start()){status.text="Could not start local HTTP server on port 9898.";return}
  server=s
  try{
   val serviceIntent=Intent(this,TransferForegroundService::class.java).setAction(TransferForegroundService.ACTION_START)
   if(android.os.Build.VERSION.SDK_INT>=26)startForegroundService(serviceIntent)else startService(serviceIntent)
  }catch(e:Exception){
   s.stop();server=null;status.text="Could not start transfer service: ${e.message?: "service error"}";return
  }
  window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  serverInfo.text="Phone file server: http://$local:9898/  •  Keep XPD open"
  busy=true;send.isEnabled=false;clear.isEnabled=false
  status.text=if(goldHen)"Preparing GoldHEN installer…" else "Submitting packages to Remote Package Installer…"
  Thread{
   var message=""
   try{
    for((index,entry) in files.withIndex()){
     runOnUiThread{status.text="Sending ${index+1}/${files.size}: ${entry.metadata?.displayTitle?:entry.name}"}
     if(goldHen){
      val result=GoldHenInstaller.install(this,host,local,entry,9898)
      if(!result.success){message="Package ${index+1} failed: ${result.message}";break}
      message=result.message
     }else{
      var c:HttpURLConnection?=null
      val result=try{
       val fileUrl="http://$local:9898/pkg/${entry.id}"
       val body=JSONObject().put("type","direct").put("packages",JSONArray().put(fileUrl))
       val title=entry.metadata?.displayTitle?.takeIf{it.isNotBlank()}
       if(title!=null)body.put("name",title)
       c=URL("http://$host:12800/api/install").openConnection() as HttpURLConnection
       c.requestMethod="POST";c.connectTimeout=10000;c.readTimeout=15000;c.doOutput=true
       c.setRequestProperty("Content-Type","application/json")
       c.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}
       val code=c.responseCode
       val reply=(if(code in 200..299)c.inputStream else c.errorStream)?.bufferedReader()?.use{it.readText()}?:""
       if(code in 200..299&&reply.contains("success",true))"RPI accepted: ${entry.name}" else "RPI rejected ${entry.name} (HTTP $code): $reply"
      }catch(e:Exception){"RPI request failed for ${entry.name}: ${e.message?: "network error"}"}
      finally{c?.disconnect()}
      if(!result.startsWith("RPI accepted:")){message=result;break}
      message=result
     }
    }
    if(message.isBlank())message="No package request was completed."
    if(message.startsWith("RPI accepted:"))message="RPI accepted the last submitted package. Confirm downloads and installation on the console. $message"
   }catch(e:Exception){message="Transfer stopped: ${e.message?: "unexpected error"}"}
   runOnUiThread{
    status.text=message
    busy=false
    send.isEnabled=files.isNotEmpty()
    clear.isEnabled=true
   }
  }.start()
 }
 private fun localIp(targetIp:String?=null):String? {
  return try {
   val target=targetIp?.let{java.net.InetAddress.getByName(it).address}
   val all=NetworkInterface.getNetworkInterfaces()?:return null
   val interfaces=Collections.list(all).filter{it.isUp&&!it.isLoopback}
   if(target!=null){
    for(n in interfaces)for(ifa in n.interfaceAddresses){
     val addr=ifa.address as? Inet4Address?:continue
     val bytes=addr.address
     val prefix=ifa.networkPrefixLength.toInt().coerceIn(0,32)
     var same=true
     for(i in 0..3){
      val bits=(prefix-i*8).coerceIn(0,8)
      val mask=if(bits==0)0 else (0xff shl (8-bits)) and 0xff
      if((bytes[i].toInt() and 255 and mask)!=(target[i].toInt() and 255 and mask)){same=false;break}
     }
     if(same&&addr.isSiteLocalAddress)return addr.hostAddress
    }
   }
   for(n in interfaces)for(a in Collections.list(n.inetAddresses))if(a is Inet4Address&&a.isSiteLocalAddress&&!a.isLoopbackAddress)return a.hostAddress
   null
  }catch(_:Exception){null}
 }
 private fun refreshQueue(){
  fileInfo.text=if(files.isEmpty())"No packages selected" else "${files.size} package(s) selected"
  queue.removeAllViews()
  if(files.isEmpty()){queue.addView(label("Your selected packages will appear here.",11f,muted).apply{setPadding(2,dp(8),2,dp(8))})}
  files.forEachIndexed{i,f->
   val row=LinearLayout(this).apply{orientation=1;setPadding(dp(12),dp(10),dp(12),dp(10));background=android.graphics.drawable.GradientDrawable().apply{setColor(Color.rgb(19,21,25));cornerRadius=dp(12).toFloat();setStroke(1,Color.rgb(45,48,55))}}
   val meta=f.metadata
   val top=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL}
   if(meta?.iconPng!=null){
    try{val bitmap=android.graphics.BitmapFactory.decodeByteArray(meta.iconPng,0,meta.iconPng.size);if(bitmap!=null){val cover=ImageView(this@MainActivity).apply{setImageBitmap(bitmap);scaleType=ImageView.ScaleType.CENTER_CROP};top.addView(cover,lp(dp(58),dp(58)).apply{rightMargin=dp(12)})}}catch(_:Exception){}
   }
   val textCol=LinearLayout(this).apply{orientation=1}
   textCol.addView(label(meta?.displayTitle?.takeIf{it.isNotBlank()}?:f.name,13f,silver).apply{maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END})
   val role=when{meta?.isDlc==true->"DLC";meta?.isUpdate==true->"UPDATE";meta?.platform=="PS4"->"BASE GAME";else->""}
   val subtitle=listOfNotNull(meta?.platform,meta?.titleId?.takeIf{it.isNotBlank()},meta?.version?.takeIf{it.isNotBlank()},role.takeIf{it.isNotBlank()}).joinToString("  •  ")
   if(subtitle.isNotBlank())textCol.addView(label(subtitle,10f,muted).apply{setPadding(0,dp(4),0,0)})
   top.addView(textCol,LinearLayout.LayoutParams(0,-2,1f));row.addView(top)
   val n=served[f.id]?:0L;val pct=if(f.size>0)(n*100/f.size).coerceIn(0,100)else 0
   row.addView(label("${f.name}  •  ${sizeText(f.size)}  •  served ${pct}%",10f,muted).apply{setPadding(0,dp(7),0,0);maxLines=2})
   val progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply{max=100;this.progress=pct;progressTintList=android.content.res.ColorStateList.valueOf(silver);progressBackgroundTintList=android.content.res.ColorStateList.valueOf(Color.rgb(48,51,58))}
   row.addView(progress,lp(-1,dp(5)).apply{topMargin=dp(8)})
   row.addView(button("REMOVE"){if(!busy){files.removeAll{it.id==f.id};served.remove(f.id);refreshQueue();saveState()}},lp(dp(88),dp(32)).apply{topMargin=dp(8)})
   queue.addView(row,lp(-1,-2).apply{topMargin=dp(7)})
  }
  send.isEnabled=files.isNotEmpty()&&!busy;clear.isEnabled=!busy
 }
 private fun sizeText(n:Long):String{if(n<0)return "Size unknown";val mb=n/1048576.0;return if(mb>=1024)String.format(Locale.US,"%.2f GB",mb/1024)else String.format(Locale.US,"%.1f MB",mb)}
 @Deprecated("Legacy result callback")
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
  super.onActivityResult(requestCode,resultCode,data)
  if(requestCode==42){
   val tree=data?.data
   if(resultCode==RESULT_OK&&tree!=null){
    val grantedFlags=(data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try{contentResolver.takePersistableUriPermission(tree,grantedFlags)}catch(_:SecurityException){}
    scanFolder(tree)
   }
   return
  }
  if(requestCode!=41||resultCode!=RESULT_OK||data==null)return
  val uris=mutableListOf<Uri>();data.clipData?.let{for(i in 0 until it.itemCount)uris.add(it.getItemAt(i).uri)};if(uris.isEmpty())data.data?.let{uris.add(it)}
  var count=0;var rejected=0;uris.forEach{u->if(files.any{it.uri==u})return@forEach
   try{contentResolver.takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(_:SecurityException){}
   if(!isValidPkg(u)){rejected++;return@forEach}
   val name=query(u,OpenableColumns.DISPLAY_NAME)?:"package.pkg"
   val metadata=PkgMetadataReader.read(contentResolver,u)
   files.add(PkgEntry(java.util.UUID.randomUUID().toString(),u,displayPkgName(name),packageSize(u),metadata));count++}
  status.text=when{count>0&&rejected>0->"$count valid PKG file(s) added; $rejected invalid file(s) skipped.";count>0->"$count valid PKG file(s) added.";rejected>0->"No valid PlayStation PKG found. Check the selected file.";else->"No new PKG files selected."};refreshQueue();saveState()
 }
 private fun displayPkgName(name:String)=if(name.lowercase(Locale.ROOT).endsWith(".pkg"))name else name+".pkg"
 private fun isValidPkg(u:Uri):Boolean = PkgMetadataReader.hasPkgMagic(contentResolver,u)
 private fun packageSize(u:Uri):Long { val column=query(u,OpenableColumns.SIZE)?.toLongOrNull(); if(column!=null&&column>=0)return column; return try{contentResolver.openFileDescriptor(u,"r")?.use{pfd->pfd.statSize.takeIf{it>=0L}?:android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use{it.channel.size()}}?:-1L}catch(_:Exception){-1L} }
 private fun query(u:Uri,col:String):String?{var c:Cursor?=null;return try{c=contentResolver.query(u,arrayOf(col),null,null,null);if(c!=null&&c.moveToFirst()){val i=c.getColumnIndex(col);if(i>=0&&!c.isNull(i))c.getString(i)else null}else null}catch(_:Exception){null}finally{c?.close()}}
 override fun onDestroy(){server?.stop();stopService(Intent(this,TransferForegroundService::class.java));window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);super.onDestroy()}
}
