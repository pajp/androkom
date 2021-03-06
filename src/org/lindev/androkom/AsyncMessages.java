package org.lindev.androkom;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import nu.dll.lyskom.AsynchMessage;
import nu.dll.lyskom.AsynchMessageReceiver;
import nu.dll.lyskom.Hollerith;
import nu.dll.lyskom.KomToken;
import nu.dll.lyskom.RpcFailure;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Message;
import android.os.Vibrator;
import android.util.Log;
import android.widget.Toast;

public class AsyncMessages implements AsynchMessageReceiver {
    private static final String TAG = "Androkom";

    public static final String ASYNC_MESSAGE_NAME = "name";
    public static final String ASYNC_MESSAGE_NEWNAME = "newname";
    public static final String ASYNC_MESSAGE_OLDNAME = "oldname";
    public static final String ASYNC_MESSAGE_FROM_ID = "from-id";
    public static final String ASYNC_MESSAGE_FROM = "from";
    public static final String ASYNC_MESSAGE_TO_ID = "to-id";
    public static final String ASYNC_MESSAGE_TO = "to";
    public static final String ASYNC_MESSAGE_MSG = "msg";

    private final App app;
    private final Set<AsyncMessageSubscriber> subscribers;

    private final List<Message> messageLog;
    private final List<Message> publicLog;

    private KomServer mKom;

    public static interface AsyncMessageSubscriber {
        public void asyncMessage(Message msg);
    }

    public String messageAsString(Message msg) {
        String str = null;

        switch (msg.what) {
        case nu.dll.lyskom.Asynch.login:
            if (mKom.getPresenceMessages()) {
                str = msg.getData().getString(ASYNC_MESSAGE_NAME)
                        + app.getString(R.string.x_logged_in);
            }
            break;

        case nu.dll.lyskom.Asynch.logout:
            if (mKom.getPresenceMessages()) {
                str = msg.getData().getString(ASYNC_MESSAGE_NAME)
                        + app.getString(R.string.x_logged_out);
            }
            break;

        case nu.dll.lyskom.Asynch.new_name:
            str = msg.getData().getString(ASYNC_MESSAGE_OLDNAME) + app.getString(R.string.x_changed_to_y);
            break;

        case nu.dll.lyskom.Asynch.send_message:
            str = msg.getData().getString(ASYNC_MESSAGE_FROM)
                    + app.getString(R.string.x_says_y)
                    + msg.getData().getString(ASYNC_MESSAGE_MSG)
                    + app.getString(R.string.x_to_y)
                    + msg.getData().getString(ASYNC_MESSAGE_TO);
            Context context = app.getBaseContext();
            if (ConferencePrefs.getVibrateForAsynch(context)) {
                Vibrator vibrator = (Vibrator) context
                        .getSystemService(Context.VIBRATOR_SERVICE);
                vibrator.vibrate(ConferencePrefs.getVibrateTime(context));
            }
            break;

        case nu.dll.lyskom.Asynch.rejected_connection:
            str = app.getString(R.string.lyskom_full);
            break;

        case nu.dll.lyskom.Asynch.sync_db:
            str = app.getString(R.string.sync_db_msg);
            break;
        }

        return str;
    }

    /**
     * Displays incoming messages as Toast events
     */
    public class MessageToaster implements AsyncMessageSubscriber {
        public void asyncMessage(Message msg) {
            final String str = messageAsString(msg);

            if (str != null) {
                int length = Toast.LENGTH_SHORT;
                if (msg.what == nu.dll.lyskom.Asynch.send_message) {
                    length = Toast.LENGTH_LONG;
                }
                //Context context = app.getBaseContext();
                if (ConferencePrefs.getToastForAsynch(app.getBaseContext())) {
                    Toast.makeText(app, str, length).show();
                }
            }
        }
    };

    public List<Message> getLog() {
        return publicLog;
    }

    public AsyncMessages(final App app, final KomServer kom) {
        mKom = kom;
        this.app = app;
        this.subscribers = new HashSet<AsyncMessageSubscriber>();
        this.messageLog = new ArrayList<Message>();
        this.publicLog = Collections.unmodifiableList(this.messageLog);
    }

    private Message processMessage(final AsynchMessage asynchMessage) {
        final Message msg = new Message();
        final Bundle b = new Bundle();
        final KomToken[] params = asynchMessage.getParameters();

        msg.what = asynchMessage.getNumber();

        switch (msg.what) {
        case nu.dll.lyskom.Asynch.login:
            b.putString(ASYNC_MESSAGE_NAME, mKom.getConferenceName(params[0].intValue()));
            break;

        case nu.dll.lyskom.Asynch.logout:
            b.putString(ASYNC_MESSAGE_NAME, mKom.getConferenceName(params[0].intValue()));
            break;

        case nu.dll.lyskom.Asynch.new_name:
            b.putString(ASYNC_MESSAGE_OLDNAME, ((Hollerith) params[1]).getContentString());
            b.putString(ASYNC_MESSAGE_NEWNAME, ((Hollerith) params[2]).getContentString());
            break;

        case nu.dll.lyskom.Asynch.send_message:
            b.putInt(ASYNC_MESSAGE_FROM_ID, params[1].intValue());
            b.putInt(ASYNC_MESSAGE_TO_ID, params[0].intValue());
            b.putString(ASYNC_MESSAGE_FROM, mKom.getConferenceName(params[1].intValue()));
            b.putString(ASYNC_MESSAGE_TO, mKom.getConferenceName(params[0].intValue()));
            b.putString(ASYNC_MESSAGE_MSG, ((Hollerith) params[2]).getContentString());
            mKom.setLatestIMSender(b.getString(ASYNC_MESSAGE_FROM));
            break;

        case nu.dll.lyskom.Asynch.new_text_old:
            Log.d(TAG, "New text created:" + params[0].intValue());
            break;

        case nu.dll.lyskom.Asynch.i_am_on:
            Log.d(TAG, "Should probably update cached data (i_am_on).");
            break;

        case nu.dll.lyskom.Asynch.sync_db:
            Log.d(TAG, "Database sync. Tell user about service interruption?");
            break;

        case nu.dll.lyskom.Asynch.leave_conf:
            Log.d(TAG, "No longer member of a conference.");
            break;

        case nu.dll.lyskom.Asynch.rejected_connection:
            Log.d(TAG, "Lyskom is full, please make space.");
            break;

        case nu.dll.lyskom.Asynch.deleted_text:
            Log.d(TAG, "Text deleted.");
            break;

        case nu.dll.lyskom.Asynch.new_text:
            Log.d(TAG, "New text created.");
            Log.d(TAG, "Trying to cache text " + params[0].intValue());
            try {
                mKom.getSession().getText(params[0].intValue());
            }
            catch (RpcFailure e) {
                e.printStackTrace();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
            break;

        case nu.dll.lyskom.Asynch.new_recipient:
            Log.d(TAG, "New recipient added to text.");
            break;

        case nu.dll.lyskom.Asynch.sub_recipient:
            Log.d(TAG, "Recipient removed from text.");
            break;

        case nu.dll.lyskom.Asynch.new_membership:
            Log.d(TAG, "New recipient added to text.");
            break;

        default:
            Log.d(TAG, "Unknown async message received#" + msg.what);
        }

        msg.setData(b);

        return msg;
    }

    public void subscribe(AsyncMessageSubscriber sub) {
        subscribers.add(sub);
    }

    public void unsubscribe(AsyncMessageSubscriber sub) {
        subscribers.remove(sub);
    }

    private class AsyncHandlerTask extends AsyncTask<AsynchMessage, Void, Message> {
        @Override
        protected Message doInBackground(final AsynchMessage... message) {
            Log.d(TAG, "AsyncHandlerTask doInBackground");
            Message msg = processMessage(message[0]);
            Log.d(TAG, "AsyncHandlerTask doInBackground done");
            return msg;
        }

        /**
         * Send the processed message to all subscribers.
         */
        @Override
        protected void onPostExecute(final Message msg) {
            Log.d(TAG, "Number of async subscribers: " + subscribers.size());
            messageLog.add(msg);
            for (AsyncMessageSubscriber subscriber : subscribers) {
                subscriber.asyncMessage(msg);
            }
            Log.d(TAG, "AsyncHandlerTask onPostExecute done");
        }
    }

    /**
     * Receives asynchronous messages from Session object. In this stage, some
     * of the messages only contains numeric id:s where we really are interested
     * in the textual representation, so we spawn a background task to process
     * the message.
     */
    public void asynchMessage(final AsynchMessage m) {
        new AsyncHandlerTask().execute(m);
    }
}
