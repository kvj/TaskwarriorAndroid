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
import org.kvj.bravo7.util.Compat;
import org.kvj.bravo7.util.DataUtil;
import org.kvj.bravo7.util.Listeners;
import org.kvj.bravo7.util.Tasks;

import java.io.CharArrayWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import kvj.taskw.App;
import kvj.taskw.R;
import kvj.taskw.sync.SSLHelper;
import kvj.taskw.ui.MainActivity;
import kvj.taskw.ui.MainListAdapter;
import kvj.taskw.ui.RunActivity;

/**
 * Created by vorobyev on 11/17/15.
 */
public class AccountController {

    private static final String CONFIRM_YN = " (yes/no) ";
    private final String accountName;
    private Thread acceptThread = null;

    private Set<NotificationType> notificationTypes = new HashSet<>();

    FileLogger fileLogger = null;

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
        List<String> params = new ArrayList<>();
        if (!TextUtils.isEmpty(socketName)) { // Have socket opened - add key
            params.add("rc.taskd.socket=" + socketName);
        }
        Collections.addAll(params, command.split(" "));
        int result = callTask(out, err, false, params.toArray(new String[0]));
        err.eat("");
        err.eat(String.format("Exit code: %d", result));
        return result;
    }

    public String id() {
        return id;
    }

    public boolean debugEnabled() {
        return fileLogger != null;
    }

    public FileLogger debugLogger() {
        return fileLogger;
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
        initLogger();
        syncSocket = openLocalSocket(socketName);
        scheduleSync(TimerType.Periodical); // Schedule on start
        loadNotificationTypes();
    }

    private void initLogger() {
        fileLogger = null;
        Map<String, String> conf = taskSettings(androidConf("debug"));
        if ("y".equalsIgnoreCase(conf.get("android.debug"))) { // Enabled
            fileLogger = new FileLogger(tasksFolder);
            debug("Profile:", accountName, id, fileLogger.logFile(tasksFolder));
        }
    }

    private void debug(Object... params) {
        if (null != fileLogger) { // Enabled
            fileLogger.log(params);
        }
    }

    private void loadNotificationTypes() {
        new Tasks.SimpleTask<String>() {

            @Override
            protected String doInBackground() {
                Map<String, String> config = taskSettings(androidConf("sync.notification"));
                if (config.isEmpty()) {
                    return "all";
                }
                return config.values().iterator().next();
            }

            @Override
            protected void onPostExecute(String s) {
                notificationTypes.clear();
                if ("all".equals(s)) { // All types
                    notificationTypes.add(NotificationType.Sync);
                    notificationTypes.add(NotificationType.Success);
                    notificationTypes.add(NotificationType.Error);
                    return;
                }
                for (String type : s.split(",")) { // Search type
                    for (NotificationType nt : NotificationType.values()) { // Check name
                        if (nt.name.equalsIgnoreCase(type.trim())) { // Found
                            notificationTypes.add(nt);
                            break;
                        }
                    }
                }
            }
        }.exec();
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

    public enum NotificationType {Sync("sync"), Success("success"), Error("error");

        private final String name;

        NotificationType(String name) {
            this.name = name;
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

    private boolean toggleSyncNotification(NotificationCompat.Builder n, NotificationType type) {
        if (notificationTypes.contains(type)) { // Have to show
            Intent intent = new Intent(controller.context(), MainActivity.class);
            intent.putExtra(App.KEY_ACCOUNT, id);
            n.setContentIntent(PendingIntent.getActivity(controller.context(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT));
            controller.notify(Controller.NotificationType.Sync, accountName, n);
            return true;
        } else {
            controller.cancel(Controller.NotificationType.Sync, accountName);
            return false;
        }
    }

    public String taskSync() {
        NotificationCompat.Builder n = controller.newNotification(accountName);
        n.setOngoing(true);
        n.setContentText("Sync is in progress");
        n.setTicker("Sync is in progress");
        n.setPriority(NotificationCompat.PRIORITY_DEFAULT);
        toggleSyncNotification(n, NotificationType.Sync);
        StringAggregator err = new StringAggregator();
        StringAggregator out = new StringAggregator();
        boolean result = callTask(out, err, "rc.taskd.socket=" + socketName, "sync");
        debug("Sync result:", result);
        logger.d("Sync result:", result, "ERR:", err.text(), "OUT:", out.text());
        n = controller.newNotification(accountName);
        n.setOngoing(false);
        if (result) { // Success
            n.setContentText("Sync complete");
            n.setPriority(NotificationCompat.PRIORITY_MIN);
            n.addAction(R.drawable.ic_action_sync, "Sync again", syncIntent("notification"));
            toggleSyncNotification(n, NotificationType.Success);
            scheduleSync(TimerType.Periodical);
            return null;
        } else {
            String error = err.text();
            debug("Sync error output:", error);
            n.setContentText("Sync failed");
            n.setTicker("Sync failed");
            n.setSubText(error);
            n.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
            n.setPriority(NotificationCompat.PRIORITY_DEFAULT);
            n.addAction(R.drawable.ic_action_sync, "Retry now", syncIntent("notification"));
            toggleSyncNotification(n, NotificationType.Error);
            scheduleSync(TimerType.AfterError);
            return error;
        }
    }

    Pattern linePatthern = Pattern.compile("^([A-Za-z0-9\\._]+)\\s+(\\S.*)$");

    private String taskSetting(String name) {
        return taskSettings(name).get(name);
    }

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
        }, errConsumer, "rc.defaultwidth=1000", "show");
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
                if (controller.BUILTIN_REPORTS.contains(key)) { // Skip builtin reports
                    return;
                }
                if (!onlyThose.isEmpty() && !onlyThose.contains(key)) {
                    return; // Skip not selected
                }
                keys.add(key);
                values.add(value);
            }
        }, errConsumer, "reports");
        if (onlyThose.isEmpty() && !keys.isEmpty()) { // All reports - remove last
            keys.remove(keys.size()-1);
            values.remove(values.size()-1);
        }
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
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
        if (result.isEmpty()) { // Invalid configuration
            result.put("next", "[next] Fail-safe report");
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

    public ReportInfo taskReportInfo(String name, final String query) {
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
                    String q = value;
                    if (!TextUtils.isEmpty(query)) { // Add query
                        q += " "+query;
                    }
                    info.query = q;
                }
                if (key.endsWith(".description")) {
                    info.description = value;
                }
            }
        }, errConsumer, "show", String.format("report.%s.", name));
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
                debug("Error in binary call: executable not found");
                throw new RuntimeException("Invalid executable");
            }
            if (null == tasksFolder) {
                debug("Error in binary call: invalid profile folder");
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
            pb.directory(tasksFolder);
            pb.environment().put("TASKRC", new File(tasksFolder, TASKRC).getAbsolutePath());
            pb.environment().put("TASKDATA", new File(tasksFolder, DATA_FOLDER).getAbsolutePath());
            Process p = pb.start();
            logger.d("Calling now:", tasksFolder, args);
//            debug("Execute:", args);
            Thread outThread = readStream(p.getInputStream(), p.getOutputStream(), out);
            Thread errThread = readStream(p.getErrorStream(), null, err);
            int exitCode = p.waitFor();
            logger.d("Exit code:", exitCode, args);
//            debug("Execute result:", exitCode);
            if (null != outThread) outThread.join();
            if (null != errThread) errThread.join();
            return exitCode;
        } catch (Exception e) {
            logger.e(e, "Failed to execute task");
            err.eat(e.getMessage());
            debug("Execute failure:");
            debug(e);
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

    private File fileFromConfig(String path) {
        if (TextUtils.isEmpty(path)) { // Invalid path
            return null;
        }
        if (path.startsWith("/")) { // Absolute
            return new File(path);
        }
        // Relative
        return new File(this.tasksFolder, path);
    }

    private class LocalSocketRunner {

        private final int port;
        private final String host;
        private final SSLSocketFactory factory;
        private final LocalServerSocket socket;

        private LocalSocketRunner(String name, Map<String, String> config) throws Exception {
            SSLHelper.TrustType trustType = SSLHelper.parseTrustType(config.get("taskd.trust"));
            String _host = config.get("taskd.server");
            int lastColon = _host.lastIndexOf(":");
            this.port = Integer.parseInt(_host.substring(lastColon + 1));
            this.host = _host.substring(0, lastColon);
            debug("Host and port:", host, port);
            if (null != fileLogger) { // Can't just call debug, because of use of fileLogger
                debug("CA file:",
                        fileLogger.logFile(fileFromConfig(config.get("taskd.ca"))));
                debug("Certificate file:",
                        fileLogger.logFile(fileFromConfig(config.get("taskd.certificate"))));
                debug("Key file:",
                        fileLogger.logFile(fileFromConfig(config.get("taskd.key"))));
            }
            this.factory = SSLHelper.tlsSocket(
                    new FileInputStream(fileFromConfig(config.get("taskd.ca"))),
                    new FileInputStream(fileFromConfig(config.get("taskd.certificate"))),
                    new FileInputStream(fileFromConfig(config.get("taskd.key"))), trustType);
            debug("Credentials loaded");
            logger.d("Connecting to:", this.host, this.port);
            this.socket = new LocalServerSocket(name);
        }

        public void accept() throws IOException {
            LocalSocket conn = socket.accept();
            logger.d("New incoming connection");
            new LocalSocketThread(conn).start();
        }

        private class LocalSocketThread extends Thread {

            private final LocalSocket socket;

            private LocalSocketThread(LocalSocket socket) {
                this.socket = socket;
            }

            private long recvSend(InputStream from, OutputStream to) throws IOException {
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
                        return bytes;
                    }
                    to.write(buffer, 0, recv);
                    to.flush();
                    bytes += recv;
                }
                logger.d("Transfer done", bytes, size);
                return bytes;
            }

            @Override
            public void run() {
                SSLSocket remoteSocket = null;
                debug("Communication taskw<->android started");
                try {
                    remoteSocket = (SSLSocket) factory.createSocket(host, port);
                    final SSLSocket finalRemoteSocket = remoteSocket;
                    Compat.levelAware(16, new Runnable() {
                        @Override
                        public void run() {
                            finalRemoteSocket.setEnabledProtocols(new String[]{"TLSv1", "TLSv1.1", "TLSv1.2"});
                        }
                    }, new Runnable() {
                        @Override
                        public void run() {
                            finalRemoteSocket.setEnabledProtocols(new String[]{"TLSv1"});
                        }
                    });
                    debug("Ready to establish TLS connection to:", host, port);
                    InputStream localInput = socket.getInputStream();
                    OutputStream localOutput = socket.getOutputStream();
                    InputStream remoteInput = remoteSocket.getInputStream();
                    OutputStream remoteOutput = remoteSocket.getOutputStream();
                    debug("Connected to taskd server");
                    logger.d("Connected, will read first piece", remoteSocket.getSession().getCipherSuite());
                    long bread = recvSend(localInput, remoteOutput);
                    long bwrite = recvSend(remoteInput, localOutput);
                    logger.d("Sync success");
                    debug("Transfer complete. Bytes sent:", bread, "Bytes received:", bwrite);
                } catch (Exception e) {
                    logger.e(e, "Failed to transfer data");
                    debug("Transfer failure");
                    debug(e);
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
    }


    private LocalServerSocket openLocalSocket(String name) {
        try {
            final Map<String, String> config = taskSettings("taskd.ca", "taskd.certificate", "taskd.key", "taskd.server", "taskd.trust");
            logger.d("Will run with config:", config);
            debug("taskd.* config:", config);
            if (!config.containsKey("taskd.server")) {
                // Not configured
                logger.d("Sync not configured - give up");
                controller.toastMessage("Sync disabled: no taskd.server value", true);
                debug("taskd.server is empty: sync disabled");
                return null;
            }
            final LocalSocketRunner runner;
            try {
                runner = new LocalSocketRunner(name, config);
            } catch (Exception e) {
                logger.e(e, "Error opening socket");
                debug(e);
                controller.toastMessage("Sync disabled: certificate load failure", true);
                return null;
            }
            acceptThread = new Thread() {
                @Override
                public void run() {
                    while (true) {
                        try {
//                            debug("Incoming connection: task binary -> android");
                            runner.accept();
                        } catch (IOException e) {
                            debug("Socket accept failed");
                            debug(e);
                            logger.w(e, "Accept failed");
                            return;
                        }
                    }
                }
            };
            acceptThread.start();
            controller.toastMessage("Sync configured", false);
            return runner.socket; // Close me later on stop
        } catch (Exception e) {
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
        String context = taskSetting("context");
        logger.d("taskList context:", context);
        debug("List query:", query, "context:", context);
        if (!TextUtils.isEmpty(context)) { // Have context configured
            String cQuery = taskSetting(String.format("context.%s", context));
            if (!TextUtils.isEmpty(cQuery)) { // Prepend context
                debug("Context query:", cQuery);
                query = String.format("(%s) %s", cQuery, query);
            }
            logger.d("Context query:", cQuery, query);
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
        logger.d("List for:", query, result.size(), context);
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
        intent.putExtra(App.KEY_EDIT_DUE, MainListAdapter.asDate(json.optString("due"), "", null));
        intent.putExtra(App.KEY_EDIT_WAIT, MainListAdapter.asDate(json.optString("wait"), "", null));
        intent.putExtra(App.KEY_EDIT_SCHEDULED, MainListAdapter.asDate(json.optString("scheduled"), "", null));
        intent.putExtra(App.KEY_EDIT_UNTIL, MainListAdapter.asDate(json.optString("until"), "", null));
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
