package com.eolwral.osmonitor.ui;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ListFragment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.util.SimpleArrayMap;
import android.support.v4.view.MenuItemCompat;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.eolwral.osmonitor.R;
import com.eolwral.osmonitor.core.DmesgInfo.dmesgInfo;
import com.eolwral.osmonitor.core.LogcatInfo.logcatInfo;
import com.eolwral.osmonitor.core.ProcessInfo.processInfo;
import com.eolwral.osmonitor.ipc.IpcMessage.ipcAction;
import com.eolwral.osmonitor.ipc.IpcMessage.ipcData;
import com.eolwral.osmonitor.ipc.IpcMessage.ipcMessage;
import com.eolwral.osmonitor.ipc.IpcService;
import com.eolwral.osmonitor.ipc.IpcService.ipcClientListener;
import com.eolwral.osmonitor.preference.Preference;
import com.eolwral.osmonitor.settings.Settings;
import com.eolwral.osmonitor.util.CommonUtil;
import com.eolwral.osmonitor.util.ProcessUtil;
import com.eolwral.osmonitor.util.UserInterfaceUtil;
import com.google.protobuf.InvalidProtocolBufferException;

public class MessageFragment extends ListFragment implements ipcClientListener {

  // print logcat format
  private enum PrintLogcatFormat {
    FORMAT_OFF, FORMAT_BRIEF, FORMAT_PROCESS, FORMAT_TAG, FORMAT_THREAD, FORMAT_RAW, FORMAT_TIME, FORMAT_THREADTIME, FORMAT_LONG;
  };

  private PrintLogcatFormat printLogcatFMT = PrintLogcatFormat.FORMAT_OFF;

  // print dmesg format
  private enum PrintDmesgFormat {
    FORMAT_OFF, FORMAT_RAW
  };

  private PrintDmesgFormat printDmesgFMT = PrintDmesgFormat.FORMAT_OFF;

  // ipc client
  private static IpcService ipc = IpcService.getInstance();
  private static boolean ipcStop = false;
  private ipcAction selectedType = ipcAction.LOGCAT_MAIN;

  // data
  private ArrayList<logcatInfo> viewLogcatData = new ArrayList<logcatInfo>();
  private ArrayList<dmesgInfo> viewDmesgData = new ArrayList<dmesgInfo>();
  private ipcAction logType = ipcAction.LOGCAT_MAIN;
  private Settings settings = null;

  // selected log
  private SimpleArrayMap<Integer, Boolean> selectedData = new SimpleArrayMap<Integer, Boolean>();
  private boolean selectedMode = false;

  // process mapping
  private ProcessUtil infoHelper = null;
  @SuppressLint("UseSparseArrays")
  private SimpleArrayMap<Integer, processInfo> map = new SimpleArrayMap<Integer, processInfo>();

  // filter
  private final static int MAXLOGCAT = 30000;
  private List<ArrayList<logcatInfo>> sourceLogcatData = new ArrayList<ArrayList<logcatInfo>>();
  private boolean[] filterLogcatArray = new boolean[logcatInfo.logPriority.SILENT_VALUE + 1];

  private ArrayList<dmesgInfo> sourceDmesgData = new ArrayList<dmesgInfo>();
  private boolean[] filterDmesgArray = new boolean[dmesgInfo.dmesgLevel.DEBUG_VALUE + 1];

  private MessageListAdapter messageList = null;
  private String filterString = "";

  // stop or start
  private boolean stopUpdate = false;
  private ImageButton stopButton = null;
  private boolean autoScrollEnd = false;

  private TextView messageCount = null;

  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // persist
    setRetainInstance(true);

    // enable fragment option menu
    setHasOptionsMenu(true);

    for (int index = 0; index < filterLogcatArray.length; index++)
      filterLogcatArray[index] = true;

    for (int index = 0; index < filterDmesgArray.length; index++)
      filterDmesgArray[index] = true;

    // settings
    settings = Settings.getInstance(getActivity().getApplicationContext());

    // process utility
    infoHelper = ProcessUtil.getInstance(getActivity().getApplicationContext(),
        true);

    // reload format
    reloadFomrat();

    // set list
    messageList = new MessageListAdapter(getActivity().getApplicationContext());
    setListAdapter(messageList);

    // create array
    for (int count = 0; count < 4; count++)
      sourceLogcatData.add(new ArrayList<logcatInfo>());
  }

  private void reloadFomrat() {
    loadDmesgFormat();
    loadLogcatFormat();
  }

  private void loadDmesgFormat() {
    switch (settings.getDmesgFormat()) {
    case 1:
      printDmesgFMT = PrintDmesgFormat.FORMAT_RAW;
      break;
    default:
      printDmesgFMT = PrintDmesgFormat.FORMAT_OFF;
      break;
    }
  }

  private void loadLogcatFormat() {
    switch (settings.getLogcatFormat()) {
    case 1:
      printLogcatFMT = PrintLogcatFormat.FORMAT_BRIEF;
      break;
    case 2:
      printLogcatFMT = PrintLogcatFormat.FORMAT_PROCESS;
      break;
    case 3:
      printLogcatFMT = PrintLogcatFormat.FORMAT_TAG;
      break;
    case 4:
      printLogcatFMT = PrintLogcatFormat.FORMAT_THREAD;
      break;
    case 5:
      printLogcatFMT = PrintLogcatFormat.FORMAT_THREADTIME;
      break;
    case 6:
      printLogcatFMT = PrintLogcatFormat.FORMAT_TIME;
      break;
    case 7:
      printLogcatFMT = PrintLogcatFormat.FORMAT_LONG;
      break;
    case 8:
      printLogcatFMT = PrintLogcatFormat.FORMAT_RAW;
      break;
    default:
      printLogcatFMT = PrintLogcatFormat.FORMAT_OFF;
      break;
    }
  }

  public View onCreateView(LayoutInflater inflater, ViewGroup container,
      Bundle savedInstanceState) {

    View v = inflater.inflate(R.layout.ui_message_fragment, container, false);

    messageCount = ((TextView) v.findViewById(R.id.id_message_count));

    return v;
  }

  @Override
  public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
    inflater.inflate(R.menu.ui_message_menu, menu);

    // sort extend menu
    MenuItem expendMenu = menu.findItem(R.id.ui_message_type);
    Spinner expendItem = (Spinner) MenuItemCompat.getActionView(expendMenu);
    expendItem.setSelection(UserInterfaceUtil.convertTypeToLoc(selectedType));

    // source menu
    expendItem.setOnItemSelectedListener(new OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view,
          int position, long id) {
        selectedType = UserInterfaceUtil.convertLocToType(position);

        // fix font color on Android 2.3.x
        if (parent.getChildAt(0) != null)
          ((TextView) parent.getChildAt(0)).setTextColor(Color.WHITE);

        // keep it going
        if (stopUpdate == true)
          stopButton.performClick();

        // force refresh
        forceRefresh();

        // restart if it has been stopped
        if (!stopUpdate)
          stopUpdate = false;
      }

      @Override
      public void onNothingSelected(AdapterView<?> arg0) {
      }
    });

    // sort extend menu
    MenuItem searchMenu = menu.findItem(R.id.ui_message_search);
    View searchItem = (View) MenuItemCompat.getActionView(searchMenu);
    MenuItemCompat.setOnActionExpandListener(searchMenu, new HiddenTypeMenu(
        expendMenu));

    // instant search
    TextView searchView = (TextView) searchItem
        .findViewById(R.id.id_action_search_text);
    searchView.setText(filterString);
    searchView.addTextChangedListener(new TextWatcher() {

      @Override
      public void onTextChanged(CharSequence cs, int arg1, int arg2, int arg3) {
        // When user changed the Text
        messageList.getFilter().filter(cs);
      }

      @Override
      public void beforeTextChanged(CharSequence arg0, int arg1, int arg2,
          int arg3) {
      }

      @Override
      public void afterTextChanged(Editable arg0) {
      }
    });

    // refresh button
    stopButton = (ImageButton) searchItem.findViewById(R.id.id_action_stop);

    if (stopUpdate)
      stopButton.setImageResource(R.drawable.ic_action_start);
    else
      stopButton.setImageResource(R.drawable.ic_action_stop);

    stopButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        stopUpdate = !stopUpdate;

        if (stopUpdate)
          stopButton.setImageResource(R.drawable.ic_action_start);
        else
          stopButton.setImageResource(R.drawable.ic_action_stop);
      }
    });

    // filter spinner
    ImageButton filterButton = (ImageButton) searchItem
        .findViewById(R.id.id_action_filter);
    filterButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        showMultiChoiceItems();
      }
    });

    super.onCreateOptionsMenu(menu, inflater);
  }

  private class HiddenTypeMenu implements MenuItemCompat.OnActionExpandListener {
    private MenuItem expendMenu = null;

    public HiddenTypeMenu(MenuItem item) {
      this.expendMenu = item;
    }

    @Override
    public boolean onMenuItemActionExpand(MenuItem item) {
      if (expendMenu != null) {
        expendMenu.setVisible(false);
        expendMenu.setEnabled(false);
      }
      return true;
    }

    @Override
    public boolean onMenuItemActionCollapse(MenuItem item) {
      if (expendMenu != null) {
        expendMenu.setVisible(true);
        expendMenu.setEnabled(true);
      }
      return true;
    }
  }

  @Override
  public void onPrepareOptionsMenu(Menu menu) {
    MenuItem expendMenu = menu.findItem(R.id.ui_message_type);
    if (selectedMode == true)
      expendMenu.setVisible(false);
    else
      expendMenu.setVisible(true);
    super.onPrepareOptionsMenu(menu);
  }

  private void forceRefresh() {
    if (logType != selectedType) {
      ipc.removeRequest(this);
      ipcAction newCommand[] = new ipcAction[1];
      newCommand[0] = selectedType;
      ipc.addRequest(newCommand, 0, this);
    }
  }

  private void showMultiChoiceItems() {
    Builder builder = new AlertDialog.Builder(getActivity());

    if (isLogcat(logType)) {
      builder.setMultiChoiceItems(R.array.ui_message_logcat_level,
          filterLogcatArray, new OnMultiChoiceClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which,
                boolean isChecked) {
              filterLogcatArray[which] = isChecked;
            }
          });
    } else {
      builder.setMultiChoiceItems(R.array.ui_message_dmesg_level,
          filterDmesgArray, new OnMultiChoiceClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which,
                boolean isChecked) {
              filterDmesgArray[which] = isChecked;
            }
          });

    }

    builder.setPositiveButton(R.string.ui_text_okay,
        new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface dialog, int id) {
            messageList.getFilter().doFilter();
          }
        });

    builder.show();
  }

  private void exportLog(String fileName) {
    if (fileName.trim().equals(""))
      return;

    if (!fileName.contains(".csv"))
      fileName += ".csv";

    try {
      File logFile = new File(Environment.getExternalStorageDirectory()
          .getPath() + "/" + fileName);

      if (logFile.exists()) {
        new AlertDialog.Builder(getActivity())
            .setTitle(R.string.ui_menu_logexport)
            .setMessage(R.string.ui_text_fileexist)
            .setPositiveButton(R.string.ui_text_okay,
                new DialogInterface.OnClickListener() {
                  public void onClick(DialogInterface dialog, int whichButton) {
                  }
                }).create().show();
        return;
      }

      logFile.createNewFile();

      int LogCount = 0;
      if (isLogcat(logType))
        LogCount = viewLogcatData.size();
      else
        LogCount = viewDmesgData.size();

      FileWriter logWriter = new FileWriter(logFile);

      final Calendar calendar = Calendar.getInstance();

      for (int index = 0; index < LogCount; index++) {

        StringBuilder logLine = new StringBuilder();

        // filter specific entries
        if (selectedMode == true && !selectedData.containsKey(index))
          continue;

        if (isLogcat(logType)) {
          calendar
              .setTimeInMillis(viewLogcatData.get(index).getSeconds() * 1000);

          logLine.append(DateFormat.format("yyyy-MM-dd hh:mm:ss",
              calendar.getTime())
              + ",");

          logLine.append(UserInterfaceUtil.getLogprority(viewLogcatData.get(index).getPriority()) + ",");
          logLine.append(viewLogcatData.get(index).getTag() + ",");

          if (viewLogcatData.get(index).getPid() == 0)
            logLine.append("System,");
          else if (map.containsKey(viewLogcatData.get(index).getPid()))
            logLine.append(infoHelper.getPackageName(map.get(
                viewLogcatData.get(index).getPid()).getName())
                + ",");
          else
            logLine.append("Unknown,");

          logLine.append(viewLogcatData.get(index).getMessage() + "\n");

        } else {

          if (viewDmesgData.get(index).getSeconds() != 0) {
            calendar
                .setTimeInMillis(viewDmesgData.get(index).getSeconds() * 1000);
            logLine.append(DateFormat.format("yyyy-MM-dd hh:mm:ss",
                calendar.getTime())
                + ",");
          }

          logLine.append(UserInterfaceUtil.getDmesgLevel(viewDmesgData.get(index).getLevel()) + ",");
          logLine.append(viewDmesgData.get(index).getMessage().toString() + "\n");
        }
        logWriter.write(logLine.toString());
      }

      logWriter.close();

      // refresh
      selectedMode = false;
      selectedData.clear();
      ActivityCompat.invalidateOptionsMenu(getActivity());
      messageList.notifyDataSetChanged();

    } catch (Exception e) {
      new AlertDialog.Builder(getActivity())
          .setTitle(R.string.ui_menu_logexport)
          .setMessage(e.getMessage())
          .setPositiveButton(R.string.ui_text_okay,
              new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                }
              }).create().show();

      return;
    }

    new AlertDialog.Builder(getActivity())
        .setTitle(R.string.ui_menu_logexport)
        .setMessage(R.string.ui_text_exportdone)
        .setPositiveButton(R.string.ui_text_okay,
            new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int whichButton) {
              }
            }).create().show();

    return;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
    case R.id.ui_menu_setting:
      onSettingClick();
      break;
    case R.id.ui_message_export:
      onExportClick();
      break;
    case R.id.ui_menu_exit:
      onExitClick();
      break;
    }
    return super.onOptionsItemSelected(item);
  }

  private void onSettingClick() {
    Intent settings = new Intent(getActivity(), Preference.class);
    settings.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    startActivity(settings);
    return;
  }

  private void onExitClick() {
    LocalBroadcastManager.getInstance(getActivity()).sendBroadcast(
        new Intent("Exit"));
    return;
  }

  private void onExportClick() {

    final Resources exportRes = getActivity().getResources();
    final Calendar calendar = Calendar.getInstance();
    final SimpleDateFormat formatter = new SimpleDateFormat(
        "yyyy-MM-dd-hh.mm.ss", Locale.getDefault());

    Builder exportDialog = new AlertDialog.Builder(getActivity());
    View exportView = LayoutInflater.from(getActivity()).inflate(
        R.layout.ui_message_export, null);
    TextView exportFile = (TextView) exportView
        .findViewById(R.id.id_export_filename);
    exportFile.setText("Log-" + formatter.format(calendar.getTime()));
    exportDialog.setView(exportView);

    exportDialog.setTitle(exportRes.getText(R.string.ui_menu_logexport));
    exportDialog.setNegativeButton(exportRes.getText(R.string.ui_text_cancel),null);

    exportDialog.setPositiveButton(exportRes.getText(R.string.ui_text_okay),
        new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface dialog, int whichButton) {
            String FileName = ((EditText) ((AlertDialog) dialog)
                .findViewById(R.id.id_export_filename)).getText().toString();
            exportLog(FileName);
          }
        });

    exportDialog.create().show();
    return;
  }

  @Override
  public void setUserVisibleHint(boolean isVisibleToUser) {
    super.setUserVisibleHint(isVisibleToUser);

    ipc.removeRequest(this);
    ipcStop = !isVisibleToUser;

    if (isVisibleToUser == true) {
      ipcAction newCommand[] = { logType, ipcAction.PROCESS };
      ipc.addRequest(newCommand, 0, this);
    }

    // reload format (re-enter)
    if (settings != null)
      reloadFomrat();
  }

  @Override
  public void onRecvData(ipcMessage result) {

    // check
    if (ipcStop == true)
      return;

    // update
    if (stopUpdate == true || result == null) {
      ipcAction newCommand[] = new ipcAction[2];
      newCommand[0] = selectedType;
      newCommand[1] = ipcAction.PROCESS;
      ipc.addRequest(newCommand, settings.getInterval(), this);
      return;
    }

    // clean up
    sourceDmesgData.clear();
    map.clear();

    // convert data
    // TODO: reuse old objects
    for (int index = 0; index < result.getDataCount(); index++) {

      try {
        ipcData rawData = result.getData(index);

        // prepare mapping table
        if (rawData.getAction() == ipcAction.PROCESS) {
          extractProcessInfo(rawData);
          continue;
        }

        if (isLogcat(rawData.getAction()))
          extractLogcatInfo(rawData);
        else if (rawData.getAction() == ipcAction.DMESG)
          extractDmesgInfo(rawData);

        logType = rawData.getAction();

      } catch (Exception e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }

    }

    // processing filter action
    messageList.getFilter().doFilter();

    // send command again
    ipcAction newCommand[] = new ipcAction[2];
    newCommand[0] = selectedType;
    newCommand[1] = ipcAction.PROCESS;
    if (selectedType != logType)
      ipc.addRequest(newCommand, 0, this);
    else
      ipc.addRequest(newCommand, settings.getInterval(), this);
  }

  private void extractLogcatInfo(ipcData rawData)
      throws InvalidProtocolBufferException {
    for (int count = 0; count < rawData.getPayloadCount(); count++) {
      logcatInfo lgInfo = logcatInfo.parseFrom(rawData.getPayload(count));
      if (sourceLogcatData.get(UserInterfaceUtil.convertTypeToLoc(rawData.getAction())).size() > MAXLOGCAT)
        sourceLogcatData.get(UserInterfaceUtil.convertTypeToLoc(rawData.getAction())).remove(0);
      sourceLogcatData.get(UserInterfaceUtil.convertTypeToLoc(rawData.getAction())).add(lgInfo);
    }
  }

  private void extractDmesgInfo(ipcData rawData)
      throws InvalidProtocolBufferException {
    for (int count = 0; count < rawData.getPayloadCount(); count++) {
      dmesgInfo dgInfo = dmesgInfo.parseFrom(rawData.getPayload(count));
      sourceDmesgData.add(dgInfo);
    }
  }

  private void extractProcessInfo(ipcData rawData)
      throws InvalidProtocolBufferException {
    for (int count = 0; count < rawData.getPayloadCount(); count++) {
      processInfo psInfo = processInfo.parseFrom(rawData.getPayload(count));
      if (!infoHelper.checkPackageInformation(psInfo.getName())) {
        infoHelper.doCacheInfo(psInfo.getUid(), psInfo.getOwner(),
            psInfo.getName());
      }
      map.put(psInfo.getPid(), psInfo);
    }
  }

  private boolean isLogcat(ipcAction logType) {
    if (logType == ipcAction.LOGCAT_MAIN || logType == ipcAction.LOGCAT_EVENT
        || logType == ipcAction.LOGCAT_SYSTEM
        || logType == ipcAction.LOGCAT_RADIO)
      return true;
    return false;
  }

  /**
   * implement viewholder class for connection list
   */
  private class ViewHolder {
    // main information
    TextView time;
    TextView tag;
    TextView level;
    TextView msg;

    // color
    int bkcolor;
  }

  private class MessageListAdapter extends BaseAdapter {

    private LayoutInflater itemInflater = null;
    private ViewHolder holder = null;
    private MessageFilter filter = null;

    public MessageListAdapter(Context mContext) {
      itemInflater = (LayoutInflater) mContext
          .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    public MessageFilter getFilter() {
      if (filter == null)
        filter = new MessageFilter();
      return filter;
    }

    @Override
    public int getCount() {
      if (isLogcat(logType))
        return viewLogcatData.size();
      return viewDmesgData.size();
    }

    public Object getItem(int position) {
      return position;
    }

    public long getItemId(int position) {
      return position;
    }

    public View getView(int position, View convertView, ViewGroup parent) {

      View sv = null;

      // prepare view
      if (convertView == null) {

        sv = (View) itemInflater.inflate(R.layout.ui_message_item, parent,
            false);

        holder = new ViewHolder();
        holder.time = ((TextView) sv.findViewById(R.id.id_message_time));
        holder.level = ((TextView) sv.findViewById(R.id.id_message_level));
        holder.tag = ((TextView) sv.findViewById(R.id.id_message_tag));
        holder.msg = ((TextView) sv.findViewById(R.id.id_message_text));

        sv.setTag(holder);
      } else {
        sv = (View) convertView;
        holder = (ViewHolder) sv.getTag();
      }

      // draw current color for each item
      if (selectedData.containsKey(position) == true)
        holder.bkcolor = getResources().getColor(R.color.selected_osmonitor);
      else if (position % 2 == 0)
        holder.bkcolor = getResources().getColor(R.color.dkgrey_osmonitor);
      else
        holder.bkcolor = getResources().getColor(R.color.black_osmonitor);
      sv.setBackgroundColor(holder.bkcolor);

      // get data
      if (isLogcat(logType) && viewLogcatData.size() > position) {
        logcatInfo item = viewLogcatData.get(position);
        if (printLogcatFMT == PrintLogcatFormat.FORMAT_OFF)
          showLogcatDefaultFormat(item);
        else
          showLogcatFormat(item);
      } else if (viewDmesgData.size() > position) {
        dmesgInfo item = viewDmesgData.get(position);

        if (printDmesgFMT == PrintDmesgFormat.FORMAT_OFF)
          showDmesgDefaultFormat(item);
        else
          showDmesgFormat(item);
      }

      // long click
      sv.setOnLongClickListener(new MenuLongClickListener(position));

      sv.setOnClickListener(new MenuShortClickListener(position));

      // offer better indicator for interactive
      sv.setOnTouchListener(new OnTouchListener() {

        @Override
        public boolean onTouch(View v, MotionEvent event) {

          switch (event.getAction()) {
          case MotionEvent.ACTION_DOWN:
            v.setBackgroundColor(getResources().getColor(
                R.color.selected_osmonitor));
            break;
          case MotionEvent.ACTION_UP:
          case MotionEvent.ACTION_CANCEL:
            ViewHolder holder = (ViewHolder) v.getTag();
            v.setBackgroundColor(holder.bkcolor);
            break;
          }
          return false;
        }
      });

      return sv;
    }

    private class MenuShortClickListener implements OnClickListener {
      private int position;

      public MenuShortClickListener(int position) {
        this.position = position;
      }

      @Override
      public void onClick(View v) {
        if (selectedMode && !selectedData.containsKey(position))
          selectedData.put(position, true);
        else
          selectedData.remove(position);

        if (selectedData.size() == 0) {
          selectedMode = false;
          ActivityCompat.invalidateOptionsMenu(getActivity());
        }

        messageList.notifyDataSetChanged();
      }

    }

    private class MenuLongClickListener implements OnLongClickListener {
      private int position;

      public MenuLongClickListener(int position) {
        this.position = position;
      }

      @Override
      public boolean onLongClick(View v) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setItems(R.array.ui_message_menu_item, new MessageItemMenu(
            position));
        builder.create().show();
        return false;
      }
    }

    private class MessageItemMenu implements DialogInterface.OnClickListener {
      private int position;

      public MessageItemMenu(int position) {
        this.position = position;
      }

      public void onClick(DialogInterface dialog, int which) {
        switch (which) {
        case 0:
          showProcessInformation(position);
          break;
        case 1:
          selectedMode = true;
          selectedData.put(position, true);
          ActivityCompat.invalidateOptionsMenu(getActivity());
          messageList.notifyDataSetChanged();
          break;
        }
      }
    }

    private void showProcessInformation(int position) {

      if (!isLogcat(logType)) {
        Toast.makeText(getActivity(),
            getActivity().getResources().getText(R.string.ui_text_notfound),
            Toast.LENGTH_LONG).show();
        return;
      }

      int pid = viewLogcatData.get(position).getPid();
      if (!map.containsKey(pid)) {
        Toast.makeText(getActivity(),
            getActivity().getResources().getText(R.string.ui_text_notfound),
            Toast.LENGTH_LONG).show();
        return;
      }

      MessageProcessFragment procView = new MessageProcessFragment(
          getActivity());
      procView.setTitle(infoHelper.getPackageName(map.get(pid).getName()));
      procView.setProcessData(map.get(pid));
      procView.show();
    }

    private void showDmesgFormat(dmesgInfo item) {

      holder.msg.setVisibility(View.VISIBLE);
      holder.level.setVisibility(View.GONE);
      holder.time.setVisibility(View.GONE);
      holder.tag.setVisibility(View.GONE);

      String textColor = CommonUtil.convertToRGB(getDmesgColor(item.getLevel()
          .getNumber()));
      holder.msg.setText(Html.fromHtml(highlightText(String.format("<%d>%s",
          item.getSeconds(), item.getMessage().toString()), filterString,
          textColor)));
    }

    private void showDmesgDefaultFormat(dmesgInfo item) {

      holder.msg.setVisibility(View.VISIBLE);
      holder.level.setVisibility(View.VISIBLE);
      holder.time.setVisibility(View.VISIBLE);
      holder.tag.setVisibility(View.GONE);

      if (item.getSeconds() != 0) {
        final Calendar calendar = Calendar.getInstance();
        final java.text.DateFormat convertTool = java.text.DateFormat
            .getDateTimeInstance();
        calendar.setTimeInMillis(item.getSeconds() * 1000);
        holder.time.setText(convertTool.format(calendar.getTime()));
      }

      holder.msg.setText(Html.fromHtml(highlightText(item.getMessage()
          .toString(), filterString, "#FFCCCCCC")));
      holder.level.setTextColor(Color.BLACK);
      holder.level
          .setBackgroundColor(getDmesgColor(item.getLevel().getNumber()));
      holder.level.setText(getDmesgTag(item.getLevel().getNumber()));
    }

    private int getDmesgColor(int value) {
      switch (value) {
      case dmesgInfo.dmesgLevel.DEBUG_VALUE:
        return settings.getDmesgDebugColor();
      case dmesgInfo.dmesgLevel.INFORMATION_VALUE:
        return settings.getDmesgInfoColor();
      case dmesgInfo.dmesgLevel.NOTICE_VALUE:
        return settings.getDmesgNoticeColor();
      case dmesgInfo.dmesgLevel.WARNING_VALUE:
        return settings.getDmesgWarningColor();
      case dmesgInfo.dmesgLevel.EMERGENCY_VALUE:
        return settings.getDmesgEmergencyColor();
      case dmesgInfo.dmesgLevel.ERROR_VALUE:
        return settings.getDmesgErrorColor();
      case dmesgInfo.dmesgLevel.ALERT_VALUE:
        return settings.getDmesgAlertColor();
      case dmesgInfo.dmesgLevel.CRITICAL_VALUE:
        return settings.getDmesgCriticalColor();
      }
      return settings.getDmesgDebugColor();
    }

    private String getDmesgTag(int value) {
      switch (value) {
      case dmesgInfo.dmesgLevel.DEBUG_VALUE:
        return "D";
      case dmesgInfo.dmesgLevel.INFORMATION_VALUE:
        return "I";
      case dmesgInfo.dmesgLevel.NOTICE_VALUE:
        return "N";
      case dmesgInfo.dmesgLevel.WARNING_VALUE:
        return "W";
      case dmesgInfo.dmesgLevel.EMERGENCY_VALUE:
        return "E";
      case dmesgInfo.dmesgLevel.ERROR_VALUE:
        return "E";
      case dmesgInfo.dmesgLevel.ALERT_VALUE:
        return "A";
      case dmesgInfo.dmesgLevel.CRITICAL_VALUE:
        return "C";
      }
      return "D";
    }

    private void showLogcatFormat(logcatInfo item) {

      final Calendar calendar = Calendar.getInstance();
      calendar.setTimeInMillis(item.getSeconds() * 1000);

      holder.level.setVisibility(View.GONE);
      holder.time.setVisibility(View.GONE);
      holder.tag.setVisibility(View.GONE);

      String textColor = CommonUtil.convertToRGB(UserInterfaceUtil.getLogcatColor(item.getPriority()));

      switch (printLogcatFMT) {
      case FORMAT_PROCESS:
        holder.msg.setText(Html.fromHtml(highlightText(String.format(
            "%s(%5d)  %s (%s)", UserInterfaceUtil.getLogcatTag(item.getPriority()),
            item.getPid(), item.getMessage().toString(), item.getTag()),
            filterString, textColor)));

        break;
      case FORMAT_TAG:
        holder.msg.setText(Html.fromHtml(highlightText(String.format(
            "%s/%-8s: %s", UserInterfaceUtil.getLogcatTag(item.getPriority()),
            item.getTag(), item.getMessage().toString()), filterString,
            textColor)));
        break;
      case FORMAT_THREAD:
        holder.msg.setText(Html.fromHtml(highlightText(String.format(
            "%s(%5d:%5d) %s", UserInterfaceUtil.getLogcatTag(item.getPriority()),
            item.getPid(), item.getTid(), item.getMessage().toString()),
            filterString, textColor)));
        break;
      case FORMAT_RAW:
        holder.msg.setText(Html.fromHtml(highlightText(
            String.format("%s", item.getMessage().toString()), filterString,
            textColor)));
        break;
      case FORMAT_TIME:
        holder.msg.setText(Html.fromHtml(highlightText(String.format(
            "%s.%03d %s/%-8s(%5d):  %s", DateFormat.format("MM-dd HH:mm:ss",
                calendar.getTime()), item.getNanoSeconds() / 1000000,
                UserInterfaceUtil.getLogcatTag(item.getPriority()), item.getTag(), item
                .getPid(), item.getMessage().toString()), filterString,
            textColor)));
        break;
      case FORMAT_THREADTIME:
        holder.msg.setText(Html.fromHtml(highlightText(String.format(
            "%s.%03d %5d %5d %s %-8s: %s", DateFormat.format("MM-dd HH:mm:ss",
                calendar.getTime()), item.getNanoSeconds() / 1000000, item
                .getPid(), item.getTid(), UserInterfaceUtil.getLogcatTag(item.getPriority()),
                item.getTag(), item.getMessage().toString()),
            filterString, textColor)));
        break;
      case FORMAT_LONG:
        holder.msg.setText(Html.fromHtml(highlightText(String.format(
            "[ %s.%03d %5d:%5d %s/%-8s ]\n%s", DateFormat.format(
                "MM-dd HH:mm:ss", calendar.getTime()),
            item.getNanoSeconds() / 1000000, item.getPid(), item.getTid(),
            UserInterfaceUtil.getLogcatTag(item.getPriority()), item.getTag(), item
                .getMessage().toString()), filterString, textColor)));
        break;
      case FORMAT_BRIEF:
      default:
        holder.msg.setText(Html.fromHtml(highlightText(String.format(
            "%s/%-8s(%5d): %s", UserInterfaceUtil.getLogcatTag(item.getPriority()),
            item.getTag(), item.getPid(), item.getMessage().toString()),
            filterString, textColor)));
        break;
      }
    }

    private void showLogcatDefaultFormat(logcatInfo item) {

      final Calendar calendar = Calendar.getInstance();
      final java.text.DateFormat convertTool = java.text.DateFormat
          .getDateTimeInstance();
      calendar.setTimeInMillis(item.getSeconds() * 1000);

      holder.level.setVisibility(View.VISIBLE);
      holder.time.setVisibility(View.VISIBLE);
      holder.tag.setVisibility(View.VISIBLE);

      holder.time.setText(convertTool.format(calendar.getTime()));
      holder.tag.setText(Html.fromHtml(highlightText(item.getTag(),
          filterString, "#FFCCCCCC")));
      holder.msg.setText(Html.fromHtml(highlightText(item.getMessage()
          .toString(), filterString, "#FFCCCCCC")));

      holder.level.setText(UserInterfaceUtil.getLogcatTag(item.getPriority()));
      holder.level.setTextColor(Color.BLACK);
      holder.level.setBackgroundColor(UserInterfaceUtil.getLogcatColor(item.getPriority()));
    }

    private String highlightText(String Msg, String HLText, String Color) {
      if (HLText.length() == 0)
        return "<font color='" + Color + "'>" + Msg + "</font>";
      return "<font color='"
          + Color
          + "'>"
          + Msg.replaceAll("(?i)(" + HLText + ")",
              "</font><font color='red'>$1</font><font color='" + Color + "'>")
          + "</font>";
    }

    public void refresh() {

      if (isLogcat(logType))
        messageCount.setText(String.format("%,d", viewLogcatData.size()));
      else
        messageCount.setText(String.format("%,d", viewDmesgData.size()));

      notifyDataSetChanged();

      if (autoScrollEnd == true) {
        getListView().setSelection(getListView().getCount() - 1);
        getListView().clearFocus();
      }
    }

    private class MessageFilter extends Filter {

      public void doFilter() {
        filter(filterString);
      }

      @Override
      protected FilterResults performFiltering(CharSequence constraint) {

        FilterResults result = new FilterResults();

        // non-filter
        if (constraint != null)
          filterString = constraint.toString().toLowerCase(Locale.getDefault());
        else
          filterString = "";

        // filter
        if (isLogcat(logType)) {
          ArrayList<logcatInfo> filteredItems = new ArrayList<logcatInfo>();
          for (int index = 0; index < sourceLogcatData.get(
              UserInterfaceUtil.convertTypeToLoc(logType)).size(); index++) {
            logcatInfo item = sourceLogcatData.get(UserInterfaceUtil.convertTypeToLoc(logType))
                .get(index);

            if (filterLogcatArray[UserInterfaceUtil.convertLogcatType(item.getPriority()
                .getNumber())] == false)
              continue;

            if (filterString.length() != 0)
              if (!item.getMessage().toLowerCase(Locale.getDefault())
                  .contains(filterString)
                  && !item.getTag().toLowerCase(Locale.getDefault())
                      .contains(filterString))
                continue;

            filteredItems.add(item);
          }
          result.count = filteredItems.size();
          result.values = filteredItems;
        } else {
          ArrayList<dmesgInfo> filteredItems = new ArrayList<dmesgInfo>();
          for (int index = 0; index < sourceDmesgData.size(); index++) {
            dmesgInfo item = sourceDmesgData.get(index);

            if (filterDmesgArray[UserInterfaceUtil.convertDmesgType(item.getLevel().getNumber())] == false)
              continue;

            if (filterString.length() != 0)
              if (!item.getMessage().toLowerCase(Locale.getDefault())
                  .contains(filterString))
                continue;

            filteredItems.add(item);
          }
          result.count = filteredItems.size();
          result.values = filteredItems;
        }

        return result;
      }

      @SuppressWarnings("unchecked")
      @Override
      protected void publishResults(CharSequence constraint,
          FilterResults results) {

        // detect user behavior
        if (getListView().getLastVisiblePosition() == (getListView().getCount() - 1))
          autoScrollEnd = true;
        else
          autoScrollEnd = false;

        if (results.values == null) {
          viewDmesgData = sourceDmesgData;
          // avoid to access before sourceLogcatData is ready
          if (UserInterfaceUtil.convertTypeToLoc(selectedType) < sourceLogcatData.size())
            viewLogcatData = sourceLogcatData
                .get(UserInterfaceUtil.convertTypeToLoc(selectedType));
        } else {

          if (isLogcat(logType))
            viewLogcatData = (ArrayList<logcatInfo>) results.values;
          else
            viewDmesgData = (ArrayList<dmesgInfo>) results.values;
        }

        refresh();
      }

    }
  }

}
