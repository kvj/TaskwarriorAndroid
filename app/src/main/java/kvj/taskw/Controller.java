package kvj.taskw;

import android.content.Context;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.Build;

import org.kvj.bravo7.log.Logger;
import org.kvj.bravo7.util.Tasks;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLSocketFactory;

import kvj.taskw.sync.SSLHelper;

/**
 * Created by vorobyev on 10/4/15.
 */
public class Controller extends org.kvj.bravo7.ng.Controller {

    private static final String SYNC_SOCKET = "taskwarrior.sync";
    private final String executable;
    private final LocalServerSocket syncSocket;
    private final String tasksFolder;

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

    public Controller(Context context, String name) {
        super(context, name);
        executable = eabiExecutable();
        tasksFolder = initTasksFolder();
        syncSocket = openLocalSocket(SYNC_SOCKET);
        new Tasks.VerySimpleTask() {

            @Override
            protected void doInBackground() {
                syncCall();
            }
        }.exec();
    }

    private void syncCall() {
        logger.d("Will call sync");
        callTask(outConsumer, errConsumer, "next");
        // callTask(outConsumer, errConsumer, "rc.color=off", "next");
        callTask(outConsumer, errConsumer, "rc.taskd.socket=" + SYNC_SOCKET, "sync");
    }

    private String eabiExecutable() {
        int rawID = R.raw.armeabi_v7a;
        String eabi = Build.CPU_ABI;
        if (eabi.equals("x86") || eabi.equals("x86_64")) {
            rawID = R.raw.x86;
        }
        try {
            File file = new File(context().getFilesDir(), "task");
            InputStream rawStream = context().getResources().openRawResource(rawID);
            FileOutputStream outputStream = new FileOutputStream(file);
            byte[] buffer = new byte[8192];
            int bytes = 0;
            while ((bytes = rawStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, bytes);
            }
            outputStream.close();
            rawStream.close();
            file.setExecutable(true, true);
            return file.getAbsolutePath();
        } catch (IOException e) {
            logger.e(e, "Error preparing file");
        }
        return null;
    }

    Pattern linePatthern = Pattern.compile("^([A-Za-z0-9\\._]+)\\s+(\\S.*)$");

    private Map<String, String> taskSettings(final String... names) {
        final Map<String, String> result = new HashMap<>();
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

    private Thread readStream(InputStream stream, final StreamConsumer consumer) {
        final BufferedReader reader;
        try {
            reader = new BufferedReader(new InputStreamReader(stream, "utf-8"));
        } catch (UnsupportedEncodingException e) {
            logger.e("Error opening stream");
            return null;
        }
        Thread thread = new Thread() {
            @Override
            public void run() {
                String line;
                try {
                    while ((line = reader.readLine()) != null) {
                        if (null != consumer) {
                            consumer.eat(line);
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

    private String initTasksFolder() {
        File folder = new File(context().getExternalFilesDir(null), "tasks");
        if (!folder.exists()) {
            folder.mkdir();
        }
        if (!folder.isDirectory()) {
            return null;
        }
        return folder.getAbsolutePath();
    }

    private synchronized boolean callTask(StreamConsumer out, StreamConsumer err, String... arguments) {
        try {
            if (null == executable) {
                throw new RuntimeException("Invalid executable");
            }
            if (null == tasksFolder) {
                throw new RuntimeException("Invalid folder");
            }
            String[] args = new String[arguments.length+3];
            args[0] = executable;
            args[1] = "rc.color=off";
            args[2] = "rc.verbose=nothing";
            for (int i = 0; i < arguments.length; i++) {
                args[i+3] = arguments[i];
            }
            ProcessBuilder pb = new ProcessBuilder(args);
            pb.directory(context().getFilesDir());
            pb.environment().put("TASKRC", "/storage/sdcard/.taskrc.android");
            pb.environment().put("TASKDATA", tasksFolder);
            Process p = pb.start();
            logger.d("Calling now:", executable, arguments.length);
            Thread outThread = readStream(p.getInputStream(), out);
            Thread errThread = readStream(p.getErrorStream(), err);
            int exitCode = p.waitFor();
            logger.d("Exit code:", exitCode, arguments.length);
            if (null != outThread) outThread.join();
            if (null != errThread) errThread.join();
            return 0 == exitCode;
        } catch (Exception e) {
            logger.e(e, "Failed to execute task");
            return false;
        }
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
            int headRead = from.read(head);
            to.write(head);
            to.flush();
            long size = 0;
            for (byte h : head) {
                size = (size << 8) + h;
            }
            long bytes = 4;
            byte[] buffer = new byte[1024];
            logger.d("Will transfer:", size, head[0], head[1], head[2], head[3], headRead, from.available());
            while (bytes < size) {
                int recv = from.read(buffer);
                logger.d("Actually get:", recv);
                if (recv == -1) {
                    return;
                }
                to.write(buffer, 0, recv);
                to.flush();
                bytes += recv;
            }
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
            final LocalServerSocket socket = new LocalServerSocket(name);
            Thread acceptThread = new Thread() {
                @Override
                public void run() {
                    while (true) {
                        try {
                            LocalSocket conn = socket.accept();
                            logger.d("New incoming connection");
                            new LocalSocketThread(config, conn).start();
                        } catch (IOException e) {
                            logger.w(e, "Accept failed");
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
}
