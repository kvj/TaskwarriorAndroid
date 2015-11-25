package kvj.taskw.data;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.kvj.bravo7.log.Logger;

import kvj.taskw.App;

/**
 * Created by kvorobyev on 11/25/15.
 */
public class BootReceiver extends BroadcastReceiver {

    Controller controller = App.controller();
    Logger logger = Logger.forInstance(this);

    @Override
    public void onReceive(Context context, Intent intent) {
        logger.i("Application started");
        controller.messageShort("Auto-sync timers have started");
    }
}
