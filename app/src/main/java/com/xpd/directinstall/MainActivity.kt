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
import java.net.Socket
import java.util.Collections
import java.util.Locale
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class MainActivity : Activity() {
 private val silver=Color.rgb(205,210,218); private val muted=Color.rgb(145,151,160)
 private val panel=Color.rgb(27,29,34); private val bg=Color.rgb(12,13,16)
 private val files=mutableListOf<PkgEntry>(); private val served=ConcurrentHashMap<String,Long>(); private val progressViews=ConcurrentHashMap<String,ProgressBar>(); private val progressLabels=ConcurrentHashMap<String,TextView>()
 private var server:PkgHttpServer?=null; private var goldHenBridge:GoldHenPayloadBridge?=null; private lateinit var ip:EditText; private lateinit var port:EditText
 private lateinit var status:TextView; private lateinit var fileInfo:TextView; private lateinit var queue:LinearLayout; private lateinit var queueSearch:EditText
 private lateinit var send:Button; private lateinit var check:Button; private lateinit var clear:Button; private lateinit var pauseButton:Button
 @Volatile private var busy=false; @Volatile private var scanning=false; @Volatile private var transferCancelled=false; @Volatile private var paused=false; private val retryEntries=mutableListOf<PkgEntry>()
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);window.statusBarColor=bg;window.navigationBarColor=bg;window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv();buildUi();server=TransferRuntime.server;restoreState();if(android.os.Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),9098)}
 private fun buildUi(){
  val root=LinearLayout(this).apply{
   orientation=1
   setPadding(dp(18),dp(8),dp(18),dp(12))
   setBackgroundColor(bg)
  }
  val toolbar=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL}
  val mark=ImageView(this).apply{setImageResource(R.drawable.ic_xpd_mark);scaleType=ImageView.ScaleType.FIT_CENTER;contentDescription="XPD"}
  toolbar.addView(mark,lp(dp(38),dp(38)).apply{rightMargin=dp(11)})
  val brand=LinearLayout(this).apply{orientation=1}
  brand.addView(label("XPD",22f,Color.WHITE).apply{typeface=android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.BOLD);letterSpacing=.04f})
  brand.addView(label("DIRECT INSTALL  •  PS4",9f,muted).apply{letterSpacing=.10f})
  toolbar.addView(brand,LinearLayout.LayoutParams(0,-2,1f))
  val menu=ImageButton(this).apply{
   setImageResource(R.drawable.ic_more);contentDescription="More options"
   setBackgroundColor(Color.TRANSPARENT);imageTintList=android.content.res.ColorStateList.valueOf(silver)
   setOnClickListener{val p=PopupMenu(this@MainActivity,this);p.menu.add("Clear library").setOnMenuItemClickListener{if(!busy){files.clear();retryEntries.clear();served.clear();refreshQueue();saveState()};true};p.menu.add("About XPD").setOnMenuItemClickListener{android.app.AlertDialog.Builder(this@MainActivity).setTitle("XPD Direct Install").setMessage("Send PlayStation 4 PKG files over your local network.").setPositiveButton("OK",null).show();true};p.show()}
  }
  toolbar.addView(menu,lp(dp(42),dp(42)))
  root.addView(toolbar,lp(-1,dp(52)).apply{bottomMargin=dp(14)})

  val scroll=ScrollView(this).apply{clipToPadding=false;fillViewport=false;overScrollMode=View.OVER_SCROLL_IF_CONTENT_SCROLLS}
  val content=LinearLayout(this).apply{orientation=1}
  val connection=card().apply{setPadding(dp(16),dp(16),dp(16),dp(16))}
  val connectionHead=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL}
  val connectionMark=ImageView(this).apply{setImageResource(R.drawable.ic_link);imageTintList=android.content.res.ColorStateList.valueOf(silver);contentDescription="Console connection"}
  connectionHead.addView(connectionMark,lp(dp(34),dp(34)).apply{rightMargin=dp(12)})
  val connectionTitles=LinearLayout(this).apply{orientation=1}
  connectionTitles.addView(label("PLAYSTATION 4",11f,silver).apply{typeface=android.graphics.Typeface.DEFAULT_BOLD;letterSpacing=.12f})
  connectionTitles.addView(label("Connect your console",15f,Color.WHITE).apply{setPadding(0,dp(3),0,0);typeface=android.graphics.Typeface.create("sans-serif-medium",0)})
  connectionHead.addView(connectionTitles,LinearLayout.LayoutParams(0,-2,1f))
  connection.addView(connectionHead)
  ip=field("Console IP address",false).apply{inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS}
  port=field("Auto",true).apply{setText("9090");visibility=View.GONE}
  check=button("TEST",true){checkReceiver()}.apply{textSize=12f}
  val addressRow=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL}
  addressRow.addView(ip,LinearLayout.LayoutParams(0,dp(50),1f).apply{rightMargin=dp(9)})
  addressRow.addView(check,lp(dp(82),dp(50)))
  connection.addView(addressRow,lp(-1,-2).apply{topMargin=dp(15)})
  connection.addView(button("DETECT CONSOLE AUTOMATICALLY"){discoverConsole()}.apply{
   textSize=12f;setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_search,0,0,0);compoundDrawablePadding=dp(7)
  },lp(-1,dp(46)).apply{topMargin=dp(10)})
  status=label("Not connected  •  Enter your PS4 IP and tap TEST.",11f,muted).apply{maxLines=3}
  connection.addView(status,lp(-1,-2).apply{topMargin=dp(12)})
  content.addView(connection,lp(-1,-2).apply{bottomMargin=dp(14)})

  val libraryCard=card().apply{setPadding(dp(15),dp(14),dp(15),dp(14))}
  val libraryHeader=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL}
  val libraryTitles=LinearLayout(this).apply{orientation=1}
  libraryTitles.addView(label("PKG LIBRARY",17f,Color.WHITE).apply{typeface=android.graphics.Typeface.create("sans-serif-medium",1)})
  fileInfo=label("0 packages",10f,muted).apply{setPadding(0,dp(3),0,0)}
  libraryTitles.addView(fileInfo)
  libraryHeader.addView(libraryTitles,LinearLayout.LayoutParams(0,-2,1f))
  clear=button("CLEAR",false){if(!busy){files.clear();retryEntries.clear();served.clear();refreshQueue();saveState()}}.apply{textSize=10f}
  libraryHeader.addView(clear,lp(dp(68),dp(36)))
  libraryCard.addView(libraryHeader)
  val addRow=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL}
  val add=button("ADD PKG FILES",true){chooseFiles()}.apply{textSize=12f;setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_folder,0,0,0);compoundDrawablePadding=dp(6)}
  addRow.addView(add,LinearLayout.LayoutParams(0,dp(48),1f).apply{rightMargin=dp(8)})
  val scan=button("SCAN FOLDER"){chooseFolder()}.apply{textSize=11f;setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_folder_open,0,0,0);compoundDrawablePadding=dp(5)}
  addRow.addView(scan,LinearLayout.LayoutParams(0,dp(48),1f))
  libraryCard.addView(addRow,lp(-1,-2).apply{topMargin=dp(13)})
  queueSearch=field("Search packages or Title ID",false).apply{
   addTextChangedListener(object:android.text.TextWatcher{
    override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
    override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){refreshQueue()}
    override fun afterTextChanged(s:android.text.Editable?){}
   })
  }
  libraryCard.addView(queueSearch,lp(-1,dp(43)).apply{topMargin=dp(11)})
  queue=LinearLayout(this).apply{orientation=1}
  libraryCard.addView(queue,lp(-1,-2).apply{topMargin=dp(5)})
  content.addView(libraryCard,lp(-1,-2).apply{bottomMargin=dp(14)})

  scroll.addView(content)
  root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
  send=button("SEND QUEUE (0)",true){sendFiles(retryEntries.isNotEmpty())}.apply{
   isEnabled=false;textSize=15f;setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_send,0,0,0);compoundDrawablePadding=dp(8)
  }
  root.addView(send,lp(-1,dp(56)).apply{topMargin=dp(10)})
  pauseButton=button("PAUSE",false){togglePause()}.apply{
   contentDescription="Pause transfer";visibility=View.GONE;setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_pause,0,0,0)
  }
  root.addView(pauseButton,lp(-1,dp(40)).apply{topMargin=dp(6)})
  val footer=label("XPD  •  PLAYSTATION 4 PACKAGE SENDER",9f,muted).apply{gravity=Gravity.CENTER;setPadding(0,dp(8),0,0);letterSpacing=.07f}
  footer.setOnClickListener{try{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/XPANGWAN/XPD-Direct-Install")))}catch(_:Exception){}}
  root.addView(footer,lp(-1,dp(25)))
  setContentView(root)
 }
 private fun prefs()=getSharedPreferences("xpd_direct_install",MODE_PRIVATE)
 private fun restoreState(){
  val savedIp=prefs().getString("console_ip","")?:""
  if(savedIp.isNotEmpty())ip.setText(savedIp)
  port.setText("9090")
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
  if(!::ip.isInitialized)return
  val arr=JSONArray()
  files.forEach{arr.put(it.uri.toString())}
  prefs().edit().putString("console_ip",ip.text.toString().trim()).putString("pkg_uris",arr.toString()).apply()
 }
 override fun onResume(){super.onResume();server=TransferRuntime.server;if(::queue.isInitialized)refreshQueue()}
 override fun onPause(){saveState();super.onPause()}
 private fun sectionHeader(s:String)=label(s,10f,silver).apply{letterSpacing=.12f;setPadding(2,0,2,8)}
 private fun card()=LinearLayout(this).apply{
  orientation=1;setPadding(14,14,14,14)
  background=android.graphics.drawable.GradientDrawable().apply{setColor(panel);cornerRadius=18f;setStroke(1,Color.rgb(48,51,58))}
 }
 private fun label(s:String,n:Float,c:Int)=TextView(this).apply{text=s;textSize=n;setTextColor(c);includeFontPadding=false;setLineSpacing(dp(2).toFloat(),1f)}
 private fun title(s:String)=label(s,12f,silver).apply{letterSpacing=.08f}
 private fun dp(v:Int)= (v*resources.displayMetrics.density).toInt()
 private fun lp(w:Int,h:Int)=LinearLayout.LayoutParams(w,h)
 private fun field(h:String,n:Boolean)=EditText(this).apply{hint=h;setHintTextColor(muted);setTextColor(Color.WHITE);textSize=14f;setSingleLine(true);setPadding(14,13,14,13);setBackgroundColor(panel);inputType=if(n)android.text.InputType.TYPE_CLASS_NUMBER else android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS}
 private fun button(s:String,primary:Boolean=false,action:()->Unit)=Button(this).apply{text=s;isAllCaps=false;textSize=12f;typeface=android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL);gravity=Gravity.CENTER;maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END;includeFontPadding=false;minHeight=0;minimumHeight=0;setPadding(dp(10),dp(9),dp(10),dp(9));setTextColor(if(primary)Color.BLACK else silver);setBackgroundTintList(android.content.res.ColorStateList.valueOf(if(primary)silver else panel));setOnClickListener{action()}}
 private fun chooseFolder(){
  if(busy||scanning)return
  val intent=Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply{
   addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
  }
  startActivityForResult(intent,42)
 }
 private fun scanFolder(treeUri:Uri){
  if(busy||scanning)return
  scanning=true;send.isEnabled=false
  status.text="Scanning folder for PlayStation .pkg files…"
  Thread{
   val discovered=mutableListOf<PkgEntry>()
   var inspected=0
   fun walk(folder:DocumentFile,depth:Int){
    if(depth>12||discovered.size>=2000||inspected>=10000)return
    val children=try{folder.listFiles()}catch(_:Exception){emptyArray()}
    for(child in children){
     if(discovered.size>=2000||inspected>=10000)break
     if(child.isDirectory){walk(child,depth+1)}
     else{
      val name=child.name?:continue
      inspected++
      if(!name.lowercase(Locale.ROOT).endsWith(".pkg"))continue
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
    scanning=false;refreshQueue();saveState()
    status.text=if(added>0)"Folder scan complete: $added PKG file(s) added from $inspected file(s) inspected." else "No new valid .pkg files found in this folder ($inspected files inspected)."
   }
  }.start()
 }
 private fun chooseFiles(){if(busy||scanning)return;val i=Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="*/*";putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true)};startActivityForResult(i,41)}
 private fun validIp(s:String)=s.matches(Regex("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$"))&&s.split(".").all{it.toIntOrNull()?.let { n -> n in 0..255 } == true}
 private fun discoverConsole(){
  status.text="Scanning your local Wi-Fi network…"
  Thread{
   val found=ConsoleDiscovery.scan(this){message->runOnUiThread{status.text=message}}
   runOnUiThread{
    when{
     found.isEmpty()->status.text="No receiver answered on ports 9090, 9021, 9020 or 12800. Enter the PS4 IP manually and tap TEST; make sure GoldHEN BinLoader or Remote Package Installer is running."
     found.size==1->{ip.setText(found[0].address);port.setText(found[0].port.toString());status.text="Found ${found[0].service} at ${found[0].address}:${found[0].port}. Check the connection before sending."}
     else->{ip.setText(found[0].address);port.setText(found[0].port.toString());status.text="Found ${found.size} possible receivers. Selected ${found[0].address} (${found[0].service}); verify before sending."}
    }
   }
  }.start()
 }
 private fun checkReceiver(){
  val host=ip.text.toString().trim()
  if(!validIp(host)){status.text="Enter a valid PlayStation IPv4 address.";return}
  check.isEnabled=false;status.text="Checking GoldHEN BinLoader (9090/9021/9020) and RPI (12800)…"
  Thread{val detected=detectReceiverPort(host);runOnUiThread{if(detected!=null){port.setText(detected.toString());status.text="Receiver detected on port $detected."}else status.text="No GoldHEN BinLoader (9090/9021/9020) or RPI (12800) receiver was detected.";check.isEnabled=true}}.start()
 }
 private fun detectReceiverPort(host:String):Int?{
  for(candidate in intArrayOf(9090,9021,9020)){
   try{
    Socket().use{socket->socket.connect(java.net.InetSocketAddress(host,candidate),1800);return candidate}
   }catch(_:Exception){}
  }
  val rpi=probeHttp(host,12800,"/api","RPI")
  if(rpi.contains("Remote Package Installer detected",true))return 12800
  return null
 }
 private fun probeHttp(host:String,p:Int,path:String,kind:String):String{
  var conn:HttpURLConnection?=null
  return try{
   conn=URL("http://$host:$p$path").openConnection() as HttpURLConnection
   conn.requestMethod="GET";conn.connectTimeout=4000;conn.readTimeout=4000
   val code=conn.responseCode
   val body=(if(code in 200..299)conn.inputStream else conn.errorStream)?.bufferedReader()?.use{it.readText()}?:""
   if(kind=="RPI"&&(body.contains("Unsupported method",true)&&body.contains("fail",true)))
    "PS4 Remote Package Installer detected on port $p. "
   else "Port $p responded (HTTP $code), but $kind was not confirmed."
  }catch(e:Exception){"No $kind response on $p: "+(e.message?:"connection failed")}finally{conn?.disconnect()}
 }
 private fun togglePause(){
  if(!busy)return
  paused=!paused
  pauseButton.contentDescription=if(paused)"Resume transfer" else "Pause transfer"
  pauseButton.setCompoundDrawablesWithIntrinsicBounds(if(paused)R.drawable.ic_play else R.drawable.ic_pause,0,0,0)
  status.text=if(paused)"Paused after the current package request. Resume to continue." else "Transfer resumed."
 }
 private fun sendFiles(retryOnly:Boolean=false){
  if(busy)return
  if(TransferRuntime.server!=null){status.text="The previous file server is still active. Wait for the console to finish downloading.";return}
  if(scanning){status.text="Wait for the folder scan to finish.";return}
  val queueToSend=if(retryOnly)retryEntries.toList() else files.toList()
  if(queueToSend.isEmpty()){status.text="Add at least one .pkg file.";return}
  val host=ip.text.toString().trim()
  if(!validIp(host)){status.text="Enter a valid PlayStation IPv4 address.";return}
  send.isEnabled=false
  status.text="Checking GoldHEN BinLoader (9090/9021/9020) and RPI 12800…"
  Thread{
   val detected=detectReceiverPort(host)
   runOnUiThread{
    send.isEnabled=true
    if(detected==null){status.text="GoldHEN BinLoader (9090/9021/9020) or Remote Package Installer (12800) was not detected. Start the BinLoader in GoldHEN and retry.";return@runOnUiThread}
    port.setText(detected.toString())
    beginTransfer(queueToSend,host,detected)
   }
  }.start()
 }
 private fun beginTransfer(queueToSend:List<PkgEntry>,host:String,p:Int){
  if(p==9090||p==9021||p==9020){beginGoldHenTransfer(queueToSend,host,p);return}
  val local=localIp(host)?:run{status.text="Could not find a phone IPv4 address on the same LAN as the console.";return}
  val activityRef=WeakReference(this)
  val queueSnapshot=queueToSend
  queueSnapshot.forEach{served.remove(it.id)}
  val servedCounts=served
  val acceptedIds=ConcurrentHashMap.newKeySet<String>()
  val submissionsComplete=java.util.concurrent.atomic.AtomicBoolean(false)
  val lastUiRefresh=AtomicLong(0L)
  val cleanupTriggered=AtomicLong(0L)
  fun allAcceptedFilesServed():Boolean = acceptedIds.isNotEmpty() && acceptedIds.all{id->
   val entry=queueSnapshot.firstOrNull{it.id==id}
   entry!=null && entry.size>0L && (servedCounts[id]?:0L)>=entry.size
  }
  val s=PkgHttpServer(contentResolver,9898,queueSnapshot,publicHost=local,onBytes={id,n->
   servedCounts.compute(id){_,old->((old?:0L)+n).let{v->val size=queueSnapshot.firstOrNull{it.id==id}?.size?:-1L;if(size>0)v.coerceAtMost(size)else v}}
   if(submissionsComplete.get() && allAcceptedFilesServed() && cleanupTriggered.compareAndSet(0L,1L)){
    activityRef.get()?.runOnUiThread{activityRef.get()?.stopLocalFileServer()}
   }
   val now=android.os.SystemClock.uptimeMillis()
   val previous=lastUiRefresh.get()
   if(now-previous>=200&&lastUiRefresh.compareAndSet(previous,now)){
    activityRef.get()?.runOnUiThread{activityRef.get()?.updatePackageProgress(id)}
   }
  },onServerError={m->activityRef.get()?.runOnUiThread{activityRef.get()?.status?.text=m}})
  TransferRuntime.server?.stop();TransferRuntime.server=null;server?.stop();server=null
  if(!s.start()){status.text="Could not start local HTTP server on port 9898.";return}
  server=s;TransferRuntime.server=s
  try{
   val serviceIntent=Intent(this,TransferForegroundService::class.java).setAction(TransferForegroundService.ACTION_START)
   if(android.os.Build.VERSION.SDK_INT>=26)startForegroundService(serviceIntent)else startService(serviceIntent)
  }catch(e:Exception){
   s.stop();server=null;TransferRuntime.server=null;status.text="Could not start transfer service: ${e.message?: "service error"}";return
  }
  retryEntries.clear()
  window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  transferCancelled=false;paused=false;busy=true;pauseButton.visibility=View.VISIBLE;pauseButton.isEnabled=true;pauseButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_pause,0,0,0);refreshQueue()
  status.text="Submitting packages to PS4 Remote Package Installer…"
  Thread{
   var message=""
   try{
    for((index,entry) in queueSnapshot.withIndex()){
     while(paused&&!transferCancelled)Thread.sleep(250)
     if(transferCancelled){message="Transfer stopped by user.";retryEntries.addAll(queueSnapshot.drop(index));break}
     runOnUiThread{status.text="Sending ${index+1}/${queueSnapshot.size}: ${entry.metadata?.displayTitle?:entry.name}"}
      var c:HttpURLConnection?=null
      val result=try{
       val fileUrl="http://$local:9898/pkg/${entry.id}"
       val body=JSONObject().put("type","direct").put("packages",JSONArray().put(fileUrl))
       val title=entry.metadata?.displayTitle?.takeIf{it.isNotBlank()}
       if(title!=null)body.put("name",title)
       if(entry.metadata?.iconPng!=null)body.put("icon_url","http://$local:9898/icon/${entry.id}")
       c=URL("http://$host:12800/api/install").openConnection() as HttpURLConnection
       c.requestMethod="POST";c.connectTimeout=10000;c.readTimeout=15000;c.doOutput=true
       c.setRequestProperty("Content-Type","application/json")
       c.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}
       val code=c.responseCode
       val reply=(if(code in 200..299)c.inputStream else c.errorStream)?.bufferedReader()?.use{it.readText()}?:""
       val accepted=try{val response=JSONObject(reply);response.optBoolean("success",false)||response.optString("status").equals("success",true)}catch(_:Exception){false}
       if(code in 200..299&&accepted){acceptedIds.add(entry.id);"RPI accepted: ${entry.name}"} else "RPI rejected ${entry.name} (HTTP $code): $reply"
      }catch(e:Exception){"RPI request failed for ${entry.name}: ${e.message?: "network error"}"}
      finally{c?.disconnect()}
      if(!result.startsWith("RPI accepted:")){message=result;retryEntries.addAll(queueSnapshot.drop(index));break}
      message=result
    }
    if(message.isBlank())message="No package request was completed."
    if(message.startsWith("RPI accepted:"))message="RPI accepted all submitted package tasks. The PS4 will download them; installation status appears on the console. $message"
   }catch(e:Exception){message="Transfer stopped: ${e.message?: "unexpected error"}"}
   submissionsComplete.set(true)
   val noAcceptedTasks=acceptedIds.isEmpty()
   val acceptedAlreadyServed=allAcceptedFilesServed()
   runOnUiThread{
    if(noAcceptedTasks){
     stopTransferServer()
     status.text=message
    }else if(acceptedAlreadyServed){
     stopLocalFileServer()
     status.text=message
    }else{
     status.text=message
    }
    busy=false;paused=false;pauseButton.isEnabled=false;pauseButton.visibility=View.GONE
    refreshQueue()
   }
  }.start()
 }
 private fun beginGoldHenTransfer(queueToSend:List<PkgEntry>,host:String,binLoaderPort:Int){
  queueToSend.forEach{served.remove(it.id)}
  val local=localIp(host)?:run{status.text="Could not find the phone IPv4 address on the same Wi-Fi network.";return}
  val activityRef=WeakReference(this)
  val queueSnapshot=queueToSend.toList()
  val servedCounts=served
  val lastUiRefresh=AtomicLong(0L)
  val cleanupTriggered=AtomicLong(0L)
  val s=PkgHttpServer(contentResolver,9898,queueSnapshot,publicHost=local,onBytes={id,n->
   servedCounts.compute(id){_,old->((old?:0L)+n).let{v->val size=queueSnapshot.firstOrNull{it.id==id}?.size?:-1L;if(size>0)v.coerceAtMost(size)else v}}
   if(queueSnapshot.all{entry->entry.size>0L&&(servedCounts[entry.id]?:0L)>=entry.size}&&cleanupTriggered.compareAndSet(0L,1L)){
    activityRef.get()?.runOnUiThread{activityRef.get()?.stopLocalFileServer()}
   }
   val now=android.os.SystemClock.uptimeMillis()
   val previous=lastUiRefresh.get()
   if(now-previous>=200&&lastUiRefresh.compareAndSet(previous,now)){
    activityRef.get()?.runOnUiThread{activityRef.get()?.updatePackageProgress(id)}
   }
  },onServerError={message->activityRef.get()?.runOnUiThread{activityRef.get()?.status?.text=message}})
  TransferRuntime.server?.stop();TransferRuntime.server=null;server?.stop();server=null
  if(!s.start()){status.text="Could not start the PKG web server on port 9898.";return}
  server=s;TransferRuntime.server=s
  try{
   val serviceIntent=Intent(this,TransferForegroundService::class.java).setAction(TransferForegroundService.ACTION_START)
   if(android.os.Build.VERSION.SDK_INT>=26)startForegroundService(serviceIntent)else startService(serviceIntent)
  }catch(e:Exception){
   s.stop();server=null;TransferRuntime.server=null
   status.text="Could not start transfer service: ${e.message?: "service error"}";return
  }
  retryEntries.clear()
  window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  transferCancelled=false;paused=false;busy=true
  pauseButton.visibility=View.VISIBLE;pauseButton.isEnabled=true
  pauseButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_pause,0,0,0)
  refreshQueue()
  status.text="Starting XPD installer through GoldHEN BinLoader $binLoaderPort…"
  val bridge=GoldHenPayloadBridge(this,queueSnapshot,local,
   onStatus={message->activityRef.get()?.runOnUiThread{activityRef.get()?.status?.text=message}},
   onFinished={ok,message->activityRef.get()?.runOnUiThread{
    val activity=activityRef.get()?:return@runOnUiThread
    activity.status.text=message
    activity.busy=false;activity.paused=false
    activity.pauseButton.isEnabled=false;activity.pauseButton.visibility=View.GONE
    if(!ok){
     activity.retryEntries.clear();activity.retryEntries.addAll(queueSnapshot)
     activity.stopTransferServer()
     activity.status.text=message
    }
    activity.refreshQueue()
   }}
  )
  goldHenBridge=bridge
  Thread{
   bridge.start(host,binLoaderPort)
  }.apply{name="XPD-GoldHEN-Launch";isDaemon=true;start()}
 }
 private fun stopLocalFileServer(){
  TransferRuntime.server?.stop();TransferRuntime.server=null;server=null
  try{startService(Intent(this,TransferForegroundService::class.java).setAction(TransferForegroundService.ACTION_STOP))}catch(_:Exception){}
  window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  status.text="PS4 downloaded all selected PKG data. Check the installation queue on the console."
  refreshQueue()
 }
 private fun stopTransferServer(){
  transferCancelled=true;paused=false
  TransferRuntime.server?.stop();TransferRuntime.server=null
  try{startService(Intent(this,TransferForegroundService::class.java).setAction(TransferForegroundService.ACTION_STOP))}catch(_:Exception){}
  server=null
  window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  goldHenBridge?.stop();goldHenBridge=null
  status.text="Transfer server stopped."
  refreshQueue()
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
  val query=if(::queueSearch.isInitialized)queueSearch.text.toString().trim().lowercase(Locale.ROOT)else ""
  val visible=files.withIndex().filter{(_,entry)->query.isBlank()||listOf(entry.name,entry.metadata?.displayTitle.orEmpty(),entry.metadata?.titleId.orEmpty(),entry.metadata?.contentId.orEmpty()).any{it.lowercase(Locale.ROOT).contains(query)}}
  fileInfo.text=when{
   files.isEmpty()->"No packages added yet"
   else->"${files.size} package(s) in library  •  ${retryEntries.size} to retry"
  }
  queue.removeAllViews();progressViews.clear();progressLabels.clear()
  if(files.isEmpty()){
   val empty=LinearLayout(this).apply{orientation=1;gravity=Gravity.CENTER;setPadding(dp(12),dp(26),dp(12),dp(26))}
   val emptyIcon=ImageView(this).apply{setImageResource(R.drawable.ic_folder);imageTintList=android.content.res.ColorStateList.valueOf(muted);contentDescription="Empty library"}
   empty.addView(emptyIcon,lp(dp(38),dp(38)))
   empty.addView(label("Your library is empty",14f,silver).apply{gravity=Gravity.CENTER;typeface=android.graphics.Typeface.DEFAULT_BOLD;setPadding(0,dp(9),0,dp(3))})
   empty.addView(label("Add a PS4 .pkg file to get started.",10f,muted).apply{gravity=Gravity.CENTER})
   queue.addView(empty,lp(-1,-2))
  }else if(visible.isEmpty()){
   queue.addView(label("No packages match this search.",11f,muted).apply{setPadding(dp(2),dp(16),dp(2),dp(12))})
  }
  visible.forEach{(index,f)->
   val row=LinearLayout(this).apply{
    orientation=1;setPadding(dp(11),dp(11),dp(11),dp(10))
    background=android.graphics.drawable.GradientDrawable().apply{setColor(Color.rgb(19,21,25));cornerRadius=dp(15).toFloat();setStroke(dp(1),Color.rgb(52,56,64))}
   }
   val top=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL}
   val meta=f.metadata
   var hasCover=false
   if(meta?.iconPng!=null){
    try{
     val bitmap=android.graphics.BitmapFactory.decodeByteArray(meta.iconPng,0,meta.iconPng.size)
     if(bitmap!=null){
      val cover=ImageView(this@MainActivity).apply{setImageBitmap(bitmap);scaleType=ImageView.ScaleType.CENTER_CROP;background=android.graphics.drawable.GradientDrawable().apply{cornerRadius=dp(10).toFloat()};clipToOutline=true;contentDescription="Package artwork"}
      top.addView(cover,lp(dp(62),dp(62)).apply{rightMargin=dp(11)});hasCover=true
     }
    }catch(_:Exception){}
   }
   if(!hasCover){
    val cover=FrameLayout(this).apply{
     background=android.graphics.drawable.GradientDrawable().apply{setColor(Color.rgb(35,38,44));cornerRadius=dp(10).toFloat()}
     val iv=ImageView(this@MainActivity).apply{setImageResource(R.drawable.ic_xpd_mark);imageTintList=android.content.res.ColorStateList.valueOf(silver);scaleType=ImageView.ScaleType.FIT_CENTER}
     addView(iv,FrameLayout.LayoutParams(dp(34),dp(34),Gravity.CENTER))
    }
    top.addView(cover,lp(dp(62),dp(62)).apply{rightMargin=dp(11)})
   }
   val textCol=LinearLayout(this).apply{orientation=1}
   val name=meta?.displayTitle?.takeIf{it.isNotBlank()}?:f.name
   textCol.addView(label(name,14f,Color.WHITE).apply{maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END;typeface=android.graphics.Typeface.create("sans-serif-medium",1)})
   val titleId=meta?.titleId?.takeIf{it.isNotBlank()}?:meta?.contentId?.takeIf{it.isNotBlank()}?:"PS4 package"
   textCol.addView(label(titleId,10f,muted).apply{setPadding(0,dp(4),0,0);maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END})
   textCol.addView(label("${sizeText(f.size)}  •  ${meta?.version?.takeIf{it.isNotBlank()}?: "PKG"}",10f,muted).apply{setPadding(0,dp(3),0,0);maxLines=1})
   top.addView(textCol,LinearLayout.LayoutParams(0,-2,1f))
   val options=ImageButton(this).apply{
    setImageResource(R.drawable.ic_more);imageTintList=android.content.res.ColorStateList.valueOf(silver);setBackgroundColor(Color.TRANSPARENT);contentDescription="Package options"
    setOnClickListener{
     val popup=PopupMenu(this@MainActivity,this)
     popup.menu.add("Move up").isEnabled=!busy&&index>0
     popup.menu.add("Move down").isEnabled=!busy&&index<files.lastIndex
     popup.menu.add("Remove package").isEnabled=!busy
     popup.setOnMenuItemClickListener{item->when(item.title.toString()){
      "Move up"->{if(!busy&&index>0){Collections.swap(files,index,index-1);refreshQueue();saveState()}}
      "Move down"->{if(!busy&&index<files.lastIndex){Collections.swap(files,index,index+1);refreshQueue();saveState()}}
      "Remove package"->{if(!busy){files.removeAll{it.id==f.id};retryEntries.removeAll{it.id==f.id};served.remove(f.id);refreshQueue();saveState()}}
     };true}
     popup.show()
    }
   }
   top.addView(options,lp(dp(34),dp(38)))
   row.addView(top)
   val n=served[f.id]?:0L
   val pct=if(f.size>0)(n*100/f.size).coerceIn(0L,100L).toInt()else 0
   val progressLabel=label(if(n>0)"${sizeText(f.size)}  •  ${pct}% transferred" else "READY  •  ${sizeText(f.size)}",9f,muted).apply{setPadding(dp(2),dp(9),dp(2),0);maxLines=1}
   row.addView(progressLabel);progressLabels[f.id]=progressLabel
   val progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply{
    max=100;this.progress=pct;progressTintList=android.content.res.ColorStateList.valueOf(silver);progressBackgroundTintList=android.content.res.ColorStateList.valueOf(Color.rgb(48,51,58));visibility=if(n>0)View.VISIBLE else View.GONE
   }
   row.addView(progress,lp(-1,dp(4)).apply{topMargin=dp(6)});progressViews[f.id]=progress
   queue.addView(row,lp(-1,-2).apply{topMargin=dp(8)})
  }
  send.text=when{
   retryEntries.isNotEmpty()->"RETRY FAILED (${retryEntries.size})"
   files.isNotEmpty()->"SEND QUEUE (${files.size})"
   else->"SEND QUEUE"
  }
  send.setCompoundDrawablesWithIntrinsicBounds(if(retryEntries.isNotEmpty())R.drawable.ic_retry else R.drawable.ic_send,0,0,0)
  send.isEnabled=files.isNotEmpty()&&!busy&&TransferRuntime.server==null
  clear.isEnabled=!busy
 }
 private fun updatePackageProgress(id:String){
  val entry=files.firstOrNull{it.id==id}?:return
  val sent=served[id]?:0L
  val pct=if(entry.size>0)(sent*100/entry.size).coerceIn(0L,100L).toInt()else 0
  progressViews[id]?.progress=pct
  progressViews[id]?.visibility=if(sent>0L)View.VISIBLE else View.GONE
  progressLabels[id]?.text=if(sent>0L)"${sizeText(entry.size)}  |  ${pct}% served" else sizeText(entry.size)
 }
 private fun sizeText(n:Long):String{if(n<0)return "Size unknown";val mb=n/1048576.0;return if(mb>=1024)String.format(Locale.US,"%.2f GB",mb/1024)else String.format(Locale.US,"%.1f MB",mb)}
 @Deprecated("Legacy result callback")
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
  super.onActivityResult(requestCode,resultCode,data)
  if(requestCode==42){
   val tree=data?.data
   if(resultCode==RESULT_OK&&tree!=null){
    val grantedFlags=((data?.flags?:0) and Intent.FLAG_GRANT_READ_URI_PERMISSION)
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
 override fun onDestroy(){window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);super.onDestroy()}
}
