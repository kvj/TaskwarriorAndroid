package kvj.taskw.data;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.kvj.bravo7.log.Logger;
import org.kvj.bravo7.util.DataUtil;
import org.kvj.bravo7.util.Listeners;
import org.kvj.bravo7.util.Tasks;

import java.io.BufferedReader;
import java.io.CharArrayWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLSocketFactory;

import kvj.taskw.App;
import kvj.taskw.R;
import kvj.taskw.sync.SSLHelper;
import kvj.taskw.ui.MainListAdapter;
import kvj.taskw.ui.RunActivity;

/**
 * Created by vorobyev on 11/17/15.
 */
public class AccountController {

    private static final String CONFIRM_YN = " (yes/no) ";
    private final String accountName;
    private Thread acceptThread = null;

    public File taskrc() {
        return new File(tasksFolder, TASKRC);
    }

    public String taskAnnotate(String uuid, String text) {
        StringAggregator err = new StringAggregator();
        if (!callTask(outConsumer, err, uuid, "annotate", escape(text))) { // Failure
            return err.text();
        }
        scheduleSync(TimerType.AfterChange);
        return null; // Success
    }

    public String taskDenotate(String uuid, String text) {
        StringAggregator err = new StringAggregator();
        if (!callTask(outConsumer, err, uuid, "denotate", escape(text))) { // Failure
            return err.text();
        }
        scheduleSync(TimerType.AfterChange);
        return null; // Success
    }

    public String taskUndo() {
        StringAggregator err = new StringAggregator();
        if (!callTask(outConsumer, err, "undo")) { // Failure
            return err.text();
        }
        scheduleSync(TimerType.AfterChange);
        return null; // Success
    }

    public String name() {
        return accountName;
    }

    private static String[] defaultFields = {"description", "urgency", "priority", "due", "wait", "scheduled", "recur", "until", "project", "tags"};

    public ReportInfo createQueryInfo(String query) {
        ReportInfo info = new ReportInfo();
        info.description = query;
        info.sort.put("urgency", false);
        info.sort.put("description", false);
        info.query = query;
        if (!query.toLowerCase().contains("status:"))
            info.query += " status:pending";
        info.priorities = taskPriority();
        for (String f : defaultFields) {
            info.fields.put(f, "");
        }
        return info;
    }

    public int taskCustom(String command, StreamConsumer out, StreamConsumer err) {
        int result = callTask(out, err, false, command.split(" "));
        err.eat("");
        err.eat(String.format("Exit code: %d", result));
        return result;
    }

    public interface TaskListener {
        public void onStart();
        public void onFinish();
        public void onQuestion(String question, DataUtil.Callback<Boolean> callback);
    }

    private Listeners<TaskListener> taskListeners = new Listeners<TaskListener>() {
        @Override
        protected void onAdd(TaskListener listener) {
            super.onAdd(listener);
            if (active) { // Run onStart
                listener.onStart();
            }
        }
    };

    public static final String TASKRC = ".taskrc.android";
    public static final String DATA_FOLDER = "data";
    private final Controller controller;
    private final String id;
    private boolean active = false;
    private final String socketName;

    Logger logger = Logger.forInstance(this);

    private final LocalServerSocket syncSocket;
    private final File tasksFolder;

    public interface StreamConsumer {
        public void eat(String line);
    }

    private class ToLogConsumer implements StreamConsumer {

        private final Logger.LoggerLevel level;
        private final String prefix;

        private ToLogConsumer(Logger.LoggerLevel level, String prefix) {
            this.level = level;
            this.prefix = prefix;
        }

        @Override
        public void eat(String line) {
            logger.log(level, prefix, line);
        }
    }

    private StreamConsumer errConsumer = new ToLogConsumer(Logger.LoggerLevel.Warning, "ERR:");
    private StreamConsumer outConsumer = new ToLogConsumer(Logger.LoggerLevel.Info, "STD:");

    public AccountController(Controller controller, String folder, String name) {
        this.controller = controller;
        this.accountName = name;
        this.id = folder;
        tasksFolder = initTasksFolder();
        socketName = UUID.randomUUID().toString().toLowerCase();
        syncSocket = openLocalSocket(socketName);
        scheduleSync(TimerType.Periodical); // Schedule on start
    }

    private class StringAggregator implements StreamConsumer {

        StringBuilder builder = new StringBuilder();

        @Override
        public void eat(String line) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(line);
        }

        private String text() {
            return builder.toString();
        }
    }

    public static class ListAggregator implements StreamConsumer {

        List<String> data = new ArrayList<>();

        @Override
        public void eat(String line) {
            data.add(line);
        }

        public List<String> data() {
            return data;
        }
    }

    public enum TimerType {Periodical("periodical"), AfterError("onerror"), AfterChange("onchange");

        private final String type;

        TimerType(String type) {
            this.type = type;
        }
    }

    public void stop() {
        controller.cancelAlarm(syncIntent("alarm"));
        if (null != syncSocket) {
            try {
                syncSocket.close();
            } catch (Exception e) {
                logger.w(e, "Failed to close socket");
            }
        }
    }

    public void scheduleSync(final TimerType type) {
        new Tasks.SimpleTask<Double>() {
            @Override
            protected Double doInBackground() {
                Map<String, String> config = taskSettings(androidConf(String.format("sync.%s", type.type)));
                if (config.isEmpty()) {
                    return 0.0;
                }
                try {
                    return Double.parseDouble(config.values().iterator().next());
                } catch (Exception e) {
                    logger.w("Failed to parse:", e.getMessage(), config);
                }
                return 0.0;
            }

            @Override
            protected void onPostExecute(Double minutes) {
                if (minutes <= 0) {
                    logger.d("Ignore schedule - not configured", type);
                    return;
                }
                Calendar c = Calendar.getInstance();
                c.add(Calendar.SECOND, (int) (minutes * 60.0));
                controller.scheduleAlarm(c.getTime(), syncIntent("alarm"));
                logger.d("Scheduled:", c.getTime(), type);
            }
        }.exec();
    }

    private String androidConf(String format) {
        return String.format("android.%s", format);
    }

    public String taskSync() {
        NotificationCompat.Builder n = controller.newNotification(accountName);
        n.setOngoing(true);
        n.setContentText("Sync is in progress");
        n.setTicker("Sync is in progress");
        n.setPriority(NotificationCompat.PRIORITY_DEFAULT);
        controller.notify(Controller.NotificationType.Sync, accountName, n);
        StringAggregator err = new StringAggregator();
        StringAggregator out = new StringAggregator();
        boolean result = callTask(out, err, "rc.taskd.socket=" + socketName, "sync");
        logger.d("Sync result:", result, "ERR:", err.text(), "OUT:", out.text());
        n = controller.newNotification(accountName);
        n.setOngoing(false);
        if (result) { // Success
            n.setContentText("Sync complete");
            n.setPriority(NotificationCompat.PRIORITY_MIN);
            n.addAction(R.drawable.ic_action_sync, "Sync again", syncIntent("notification"));
            controller.notify(Controller.NotificationType.Sync, accountName, n);
            scheduleSync(TimerType.Periodical);
            return null;
        } else {
            String error = err.text();
            n.setContentText("Sync failed");
            n.setTicker("Sync failed");
            n.setSubText(error);
            n.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
            n.setPriority(NotificationCompat.PRIORITY_DEFAULT);
            n.addAction(R.drawable.ic_action_sync, "Retry now", syncIntent("notification"));
            controller.notify(Controller.NotificationType.Sync, accountName, n);
            scheduleSync(TimerType.AfterError);
            return error;
        }
    }

    Pattern linePatthern = Pattern.compile("^([A-Za-z0-9\\._]+)\\s+(\\S.*)$");

    private Map<String, String> taskSettings(final String... names) {
        final Map<String, String> result = new LinkedHashMap<>();
        callTask(new StreamConsumer() {
            @Override
            public void eat(String line) {
                Matcher m = linePatthern.matcher(line);
                if (m.find()) {
                    String keyName = m.group(1).trim();
                    String keyValue = m.group(2).trim();
                    for (String name : names) {
                        if (name.equalsIgnoreCase(keyName)) {
                            result.put(name, keyValue);
                            break;
                        }
                    }
                }
            }
        }, errConsumer, "show");
        return result;
    }

    abstract private class PatternLineConsumer implements StreamConsumer {

        @Override
        public void eat(String line) {
            Matcher m = linePatthern.matcher(line);
            if (m.find()) {
                String keyName = m.group(1).trim();
                String keyValue = m.group(2).trim();
                eat(keyName, keyValue);
            }
        }

        abstract void eat(String key, String value);
    }

    public Map<String, String> taskReports() {
        final List<String> onlyThose = new ArrayList<>(); // Save names of pre-configured reports here
        Map<String, String> settings = taskSettings(androidConf("reports"),
                                                    androidConf("report.default"));
        String list = settings.get(androidConf("reports"));
        String defaultReport = settings.get(androidConf("report.default"));
        if (TextUtils.isEmpty(defaultReport)) {
            defaultReport = "next";
        }
        if (!TextUtils.isEmpty(list)) {
            onlyThose.addAll(split2(list, ","));
        }
        final List<String> keys = new ArrayList<>();
        final List<String> values = new ArrayList<>();
        callTask(new PatternLineConsumer() {

            @Override
            void eat(String key, String value) {
                if (!onlyThose.isEmpty() && !onlyThose.contains(key)) {
                    return; // Skip
                }
                keys.add(key);
                values.add(value);
            }
        }, errConsumer, "reports");
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
//        logger.d("Reports:", keys, values, defaultReport, list, onlyThose, settings);
        if (keys.contains(defaultReport)) {
            // Move default to the top
            int index = keys.indexOf(defaultReport);
            keys.add(0, keys.get(index));
            keys.remove(index+1);
            values.add(0, values.get(index));
            values.remove(index + 1);
        }
        for (int i = 0; i < keys.size(); i++) {
            result.put(keys.get(i), values.get(i));
        }
//        logger.d("Reports after sort:", keys, values, defaultReport, result);
        return result;
    }

    public static List<String> split2(String src, String sep) {
        List<String> result = new ArrayList<>();
        int start = 0;
        while (true) {
            int index = src.indexOf(sep, start);
            if (index == -1) {
                result.add(src.substring(start).trim());
                return result;
            }
            result.add(src.substring(start, index).trim());
            start = index+sep.length();
        }
    }

    public ReportInfo taskReportInfo(String name) {
        final ReportInfo info = new ReportInfo();
        callTask(new PatternLineConsumer() {

            @Override
            void eat(String key, String value) {
                if (key.endsWith(".columns")) {
                    String[] parts = value.split(",");
                    for (String p : parts) {
                        String name = p;
                        String type = "";
                        if (p.contains(".")) {
                            name = p.substring(0, p.indexOf("."));
                            type = p.substring(p.indexOf(".")+1);
                        }
                        info.fields.put(name, type);
                    }
                }
                if (key.endsWith(".sort")) {
                    String[] parts = value.split(",");
                    for (String p : parts) {
                        if (p.endsWith("/")) p = p.substring(0, p.length()-1);
                        info.sort.put(p.substring(0, p.length()-1), p.charAt(p.length()-1) == '+');
                    }
                }
                if (key.endsWith(".filter")) {
                    info.query = value;
                }
                if (key.endsWith(".description")) {
                    info.description = value;
                }
            }
        }, errConsumer, "show", String.format("report.%s", name));
        info.priorities = taskPriority();
        if (!info.sort.containsKey("description")) {
            info.sort.put("description", true);
        }
        return info;
    }

    public List<String> taskPriority() {
        // Get all priorities
        final List<String> result = new ArrayList<>();
        callTask(new PatternLineConsumer() {

            @Override
            void eat(String key, String value) {
                result.addAll(split2(value, ","));
                logger.d("Parsed priority:", value, result);
            }
        }, errConsumer, "show", "uda.priority.values");
        return result;
    }

    private Thread readStream(InputStream stream, final OutputStream outputStream,
                              final StreamConsumer consumer) {
        final Reader reader;
        try {
            reader = new InputStreamReader(stream, "utf-8");
        } catch (UnsupportedEncodingException e) {
            logger.e("Error opening stream");
            return null;
        }
        Thread thread = new Thread() {
            @Override
            public void run() {

                try {
                    CharArrayWriter line = new CharArrayWriter();
                    int ch = -1;
                    while ((ch = reader.read()) >= 0) {
                        if (ch == '\n') {
                            // New line
                            if (null != consumer) {
                                consumer.eat(line.toString());
                                line.reset();
                            }
                            continue;
                        }
                        line.write(ch);
                        if (null != outputStream && line.size() > CONFIRM_YN.length()) {
                            if (line.toString().substring(line.size()-CONFIRM_YN.length()).equals(
                                CONFIRM_YN)) {
                                // Ask for confirmation
                                final String question = line.toString().substring(0, line.size()
                                                                                     - CONFIRM_YN
                                                                                         .length()).trim();
                                listeners().emit(new Listeners.ListenerEmitter<TaskListener>() {
                                    @Override
                                    public boolean emit(TaskListener listener) {
                                        listener.onQuestion(question, new DataUtil.Callback<Boolean>() {
                                            @Override
                                            public boolean call(Boolean value) {
                                                try {
                                                    outputStream.write(String.format("%s\n", value? "yes": "no").getBytes("utf-8"));
                                                } catch (IOException e) {
                                                    e.printStackTrace();
                                                }
                                                return true;
                                            }
                                        });
                                        return true; // Only one call
                                    }
                                });
                            }
                        }
                    }
                    if (line.size() > 0) {
                        // Last line
                        if (null != consumer) {
                            consumer.eat(line.toString());
                        }
                    }
                } catch (Exception e) {
                    logger.e(e, "Error reading stream");
                } finally {
                    try {
                        reader.close();
                    } catch (IOException e) {
                    }
                }
            }
        };
        thread.start();
        return thread;
    }

    private File initTasksFolder() {
        File folder = new File(controller.context().getExternalFilesDir(null), id);
        if (!folder.exists() || !folder.isDirectory()) {
            return null;
        }
        return folder;
    }

    private synchronized int callTask(StreamConsumer out, StreamConsumer err, boolean api, String... arguments) {
        active = true;
        taskListeners.emit(new Listeners.ListenerEmitter<TaskListener>() {
            @Override
            public boolean emit(TaskListener listener) {
                listener.onStart();
                return true;
            }
        });
        try {
            if (null == controller.executable) {
                throw new RuntimeException("Invalid executable");
            }
            if (null == tasksFolder) {
                throw new RuntimeException("Invalid folder");
            }
            List<String> args = new ArrayList<>();
            args.add(controller.executable);
            args.add("rc.color=off");
            if (api) {
                args.add("rc.confirmation=off");
                args.add("rc.verbose=nothing");
            } else {
                args.add("rc.verbose=none");
            }
            Collections.addAll(args, arguments);
            ProcessBuilder pb = new ProcessBuilder(args);
            pb.directory(controller.context().getFilesDir());
            pb.environment().put("TASKRC", new File(tasksFolder, TASKRC).getAbsolutePath());
            pb.environment().put("TASKDATA", new File(tasksFolder, DATA_FOLDER).getAbsolutePath());
            Process p = pb.start();
            logger.d("Calling now:", tasksFolder, args);
            Thread outThread = readStream(p.getInputStream(), p.getOutputStream(), out);
            Thread errThread = readStream(p.getErrorStream(), null, err);
            int exitCode = p.waitFor();
            logger.d("Exit code:", exitCode, args);
            if (null != outThread) outThread.join();
            if (null != errThread) errThread.join();
            return exitCode;
        } catch (Exception e) {
            logger.e(e, "Failed to execute task");
            err.eat(e.getMessage());
            return 255;
        } finally {
            taskListeners.emit(new Listeners.ListenerEmitter<TaskListener>() {
                @Override
                public boolean emit(TaskListener listener) {
                    listener.onFinish();
                    return true;
                }
            });
            active = false;
        }
    }

    private boolean callTask(StreamConsumer out, StreamConsumer err, String... arguments) {
        int result = callTask(out, err, true, arguments);
        return result == 0;
    }

    private class LocalSocketThread extends Thread {

        private final Map<String, String> config;
        private final LocalSocket socket;

        private LocalSocketThread(Map<String, String> config, LocalSocket socket) {
            this.config = config;
            this.socket = socket;
        }

        private void recvSend(InputStream from, OutputStream to) throws IOException {
            byte[] head = new byte[4]; // Read it first
            from.read(head);
            to.write(head);
            to.flush();
            long size = ByteBuffer.wrap(head, 0, 4).order(ByteOrder.BIG_ENDIAN).getInt();
            long bytes = 4;
            byte[] buffer = new byte[1024];
            logger.d("Will transfer:", size);
            while (bytes < size) {
                int recv = from.read(buffer);
//                logger.d("Actually get:", recv);
                if (recv == -1) {
                    return;
                }
                to.write(buffer, 0, recv);
                to.flush();
                bytes += recv;
            }
            logger.d("Transfer done", bytes, size);
        }

        @Override
        public void run() {
            Socket remoteSocket = null;
            try {
                String host = config.get("taskd.server");
                int lastColon = host.lastIndexOf(":");
                int port = Integer.parseInt(host.substring(lastColon + 1));
                host = host.substring(0, lastColon);
                SSLSocketFactory factory = SSLHelper.tlsSocket(
                    new FileInputStream(config.get("taskd.ca")),
                    new FileInputStream(config.get("taskd.certificate")),
                    new FileInputStream(config.get("taskd.key")));
                logger.d("Connecting to:", host, port);
                remoteSocket = factory.createSocket(host, port);
                InputStream localInput = socket.getInputStream();
                OutputStream localOutput = socket.getOutputStream();
                InputStream remoteInput = remoteSocket.getInputStream();
                OutputStream remoteOutput = remoteSocket.getOutputStream();
                logger.d("Connected, will read first piece");
                recvSend(localInput, remoteOutput);
                recvSend(remoteInput, localOutput);
                logger.d("Sync success");
            } catch (Exception e) {
                logger.e(e, "Failed to transfer data");
            } finally {
                if (null != remoteSocket) {
                    try {
                        remoteSocket.close();
                    } catch (IOException e) {
                    }
                }
                try {
                    socket.close();
                } catch (IOException e) {
                }
            }
        }
    }

    private LocalServerSocket openLocalSocket(String name) {
        try {
            final Map<String, String> config = taskSettings("taskd.ca", "taskd.certificate", "taskd.key", "taskd.server");
            logger.d("Will run with config:", config);
            if (!config.containsKey("taskd.server")) {
                // Not configured
                logger.d("Sync not configured - give up");
                return null;
            }
            final LocalServerSocket socket = new LocalServerSocket(name);
            acceptThread = new Thread() {
                @Override
                public void run() {
                    while (true) {
                        try {
                            LocalSocket conn = socket.accept();
                            logger.d("New incoming connection");
                            new LocalSocketThread(config, conn).start();
                        } catch (IOException e) {
                            logger.w(e, "Accept failed");
                            return;
                        }
                    }
                }
            };
            acceptThread.start();
            return socket; // Close me later on stop
        } catch (IOException e) {
            logger.e(e, "Failed to open local socket");
        }
        return null;
    }

    public List<JSONObject> taskList(String query) {
        if (TextUtils.isEmpty(query)) {
            query = "status:pending";
        } else {
            query = String.format("(%s)", query);
        }
        final List<JSONObject> result = new ArrayList<>();
        List<String> params = new ArrayList<>();
        params.add("rc.json.array=off");
        params.add("export");
        params.add(escape(query));
        callTask(new StreamConsumer() {
            @Override
            public void eat(String line) {
                if (!TextUtils.isEmpty(line)) {
                    try {
                        result.add(new JSONObject(line));
                    } catch (Exception e) {
                        logger.e(e, "Not JSON object:", line);
                    }
                }
            }
        }, errConsumer, params.toArray(new String[0]));
        logger.d("List for:", query, result.size());
        return result;
    }

    public static String escape(String query) {
        return query.replace(" ", "\\ "); //.replace("(", "\\(").replace(")", "\\)");
    }

    public Intent intentForRunTask() {
        Intent intent = new Intent(controller.context(), RunActivity.class);
        intent.putExtra(App.KEY_ACCOUNT, id);
        return intent;
    }

    public boolean intentForEditor(Intent intent, String uuid) {
        intent.putExtra(App.KEY_ACCOUNT, id);
        List<String> priorities = taskPriority();
        if (TextUtils.isEmpty(uuid)) { // Done - new item
            intent.putExtra(App.KEY_EDIT_PRIORITY, priorities.indexOf(""));
            return true;
        }
        List<JSONObject> jsons = taskList(uuid);
        if (jsons.isEmpty()) { // Failed
            return false;
        }
        JSONObject json = jsons.get(0);
        int priorityIndex = priorities.indexOf(json.optString("priority", ""));
        if (-1 == priorityIndex) {
            priorityIndex = priorities.indexOf("");
        }
        intent.putExtra(App.KEY_EDIT_PRIORITY, priorityIndex);
        intent.putExtra(App.KEY_EDIT_UUID, json.optString("uuid"));
        intent.putExtra(App.KEY_EDIT_DESCRIPTION, json.optString("description"));
        intent.putExtra(App.KEY_EDIT_PROJECT, json.optString("project"));
        JSONArray tags = json.optJSONArray("tags");
        if (null != tags) {
            intent.putExtra(App.KEY_EDIT_TAGS, MainListAdapter.join(" ", MainListAdapter.array2List(tags)));
        }
        intent.putExtra(App.KEY_EDIT_DUE, MainListAdapter.asDate(json.optString("due"), "", MainListAdapter.formattedFormat));
        intent.putExtra(App.KEY_EDIT_WAIT, MainListAdapter.asDate(json.optString("wait"), "", MainListAdapter.formattedFormat));
        intent.putExtra(App.KEY_EDIT_SCHEDULED, MainListAdapter.asDate(json.optString("scheduled"), "", MainListAdapter.formattedFormat));
        intent.putExtra(App.KEY_EDIT_UNTIL, MainListAdapter.asDate(json.optString("until"), "", MainListAdapter.formattedFormat));
        intent.putExtra(App.KEY_EDIT_RECUR, json.optString("recur"));
        return true;
    }

    public String taskDone(String uuid) {
        StringAggregator err = new StringAggregator();
        if (!callTask(outConsumer, err, uuid, "done")) { // Failure
            return err.text();
        }
        scheduleSync(TimerType.AfterChange);
        return null; // Success
    }

    public String taskDelete(String uuid) {
        StringAggregator err = new StringAggregator();
        if (!callTask(outConsumer, err, uuid, "delete")) { // Failure
            return err.text();
        }
        scheduleSync(TimerType.AfterChange);
        return null; // Success
    }

    public String taskStart(String uuid) {
        StringAggregator err = new StringAggregator();
        if (!callTask(outConsumer, err, uuid, "start")) { // Failure
            return err.text();
        }
        scheduleSync(TimerType.AfterChange);
        return null; // Success
    }

    public String taskStop(String uuid) {
        StringAggregator err = new StringAggregator();
        if (!callTask(outConsumer, err, uuid, "stop")) { // Failure
            return err.text();
        }
        scheduleSync(TimerType.AfterChange);
        return null; // Success
    }

    public String taskLog(List<String> changes) {
        List<String> params = new ArrayList<>();
        params.add("log");
        for (String change : changes) { // Copy non-empty
            if (!TextUtils.isEmpty(change)) {
                params.add(change);
            }
        }
        StringAggregator err = new StringAggregator();
        if (!callTask(outConsumer, err, params.toArray(new String[params.size()]))) { // Failure
            return err.text();
        }
        scheduleSync(TimerType.AfterChange);
        return null; // Success
    }


    public String taskAdd(List<String> changes) {
        List<String> params = new ArrayList<>();
        params.add("add");
        for (String change : changes) { // Copy non-empty
            if (!TextUtils.isEmpty(change)) {
                params.add(change);
            }
        }
        StringAggregator err = new StringAggregator();
        if (!callTask(outConsumer, err, params.toArray(new String[params.size()]))) { // Failure
            return err.text();
        }
        scheduleSync(TimerType.AfterChange);
        return null; // Success
    }

    public String taskModify(String uuid, List<String> changes) {
        List<String> params = new ArrayList<>();
        params.add(uuid);
        params.add("modify");
        for (String change : changes) { // Copy non-empty
            if (!TextUtils.isEmpty(change)) {
                params.add(change);
            }
        }
        StringAggregator err = new StringAggregator();
        if (!callTask(outConsumer, err, params.toArray(new String[0]))) { // Failure
            return err.text();
        }
        scheduleSync(TimerType.AfterChange);
        return null; // Success
    }

    public Listeners<TaskListener> listeners() {
        return taskListeners;
    }

    public PendingIntent syncIntent(String type) {
        Intent intent = new Intent(controller.context(), SyncIntentReceiver.class);
        intent.putExtra(App.KEY_ACCOUNT, id);
        intent.setData(Uri.fromParts("tw", type, id));
        return PendingIntent.getBroadcast(controller.context(), App.SYNC_REQUEST, intent, PendingIntent.FLAG_CANCEL_CURRENT);
    }
}
