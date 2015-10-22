package kvj.taskw;

import android.content.Context;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.Build;

import org.kvj.bravo7.log.Logger;
import org.kvj.bravo7.util.Tasks;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;

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
        callTask(outConsumer, errConsumer, "--version");
        callTask(outConsumer, errConsumer, "rc.taskd.socket=" + SYNC_SOCKET, "rc.color=off", "sync", "init");
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

    private boolean readStream(InputStream stream, final StreamConsumer consumer) {
        final BufferedReader reader;
        try {
            reader = new BufferedReader(new InputStreamReader(stream, "utf-8"));
        } catch (UnsupportedEncodingException e) {
            logger.e("Error opening stream");
            return false;
        }
        new Tasks.VerySimpleTask(

        ) {
            @Override
            protected void doInBackground() {
                logger.d("Ready to listen stream");
                String line;
                try {
                    while ((line = reader.readLine()) != null) {
                        if (null != consumer) {
                            consumer.eat(line);
                        }
                    }
                    reader.close();
                } catch (Exception e) {
                    logger.e(e, "Error reading stream");
                }
            }
        }.exec();
        return true;
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
            String[] args = new String[arguments.length+1];
            args[0] = executable;
            for (int i = 0; i < arguments.length; i++) {
                args[i+1] = arguments[i];
            }
            ProcessBuilder pb = new ProcessBuilder(args);
            pb.directory(context().getFilesDir());
            pb.environment().put("TASKRC", "/storage/sdcard/.taskrc.android");
            pb.environment().put("TASKDATA", tasksFolder);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            logger.d("Calling now:", executable, arguments.length);
            readStream(p.getInputStream(), out);
//            readStream(p.getErrorStream(), err);
            int exitCode = p.waitFor();
            logger.d("Exit code:", exitCode, arguments.length);
            return 0 == exitCode;
        } catch (Exception e) {
            logger.e(e, "Failed to execute task");
            return false;
        }
    }

    private LocalServerSocket openLocalSocket(String name) {
        try {
            final LocalServerSocket socket = new LocalServerSocket(name);
            Thread acceptThread = new Thread() {
                @Override
                public void run() {
                    logger.d("Ready to accept connections");
                    while (true) {
                        try {
                            LocalSocket conn = socket.accept();
                            logger.d("New incoming connection", conn.getRemoteSocketAddress());
                            byte[] head = new byte[4];
                            conn.getInputStream().read(head);
                            logger.d("Head:", head);
                            conn.close();
                        } catch (IOException e) {
                            logger.w(e, "Accept failed");
                        }
                    }
                }
            };
            acceptThread.start();
            Thread.yield();
            return socket; // Close me later on stop
        } catch (IOException e) {
            logger.e(e, "Failed to open local socket");
        }
        return null;
    }
}
