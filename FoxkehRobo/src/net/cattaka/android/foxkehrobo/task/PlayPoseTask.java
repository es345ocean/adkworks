
package net.cattaka.android.foxkehrobo.task;

import net.cattaka.android.foxkehrobo.Constants;
import net.cattaka.android.foxkehrobo.core.IAppStub;
import net.cattaka.android.foxkehrobo.data.FrPacket;
import net.cattaka.android.foxkehrobo.data.OpCode;
import net.cattaka.android.foxkehrobo.entity.ActionModel;
import net.cattaka.android.foxkehrobo.entity.PoseModel;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.util.Log;

public class PlayPoseTask extends AsyncTask<ActionModel, Object, Void> {
    private IAppStub mAppStub;

    private PlayPoseTaskListener mListener;

    public interface PlayPoseTaskListener {
        public void onPlayPoseTaskUpdate(String name, PoseModel model, int pos, int num);

        public void onPlayPoseTaskFinish();
    }

    public PlayPoseTask(IAppStub appStub, PlayPoseTaskListener listener) {
        super();
        mAppStub = appStub;
        mListener = listener;
    }

    @Override
    protected Void doInBackground(ActionModel... actionModels) {
        outer: for (ActionModel actionModel : actionModels) {
            if (actionModel.getPoseModels() == null) {
                continue;
            }
            int idx = -1;
            for (PoseModel poseModel : actionModel.getPoseModels()) {
                idx++;
                if (isCancelled()) {
                    break outer;
                }
                byte[] data = poseModel.toPose();
                publishProgress(actionModel.getName(), poseModel, idx, actionModel.getPoseModels()
                        .size());
                FrPacket packet = new FrPacket(OpCode.POSE, data.length, data);
                mAppStub.getServiceWrapper().sendPacket(packet);
                try {
                    long limit = (poseModel.getTime() != null) ? poseModel.getTime() : 0;
                    long time = SystemClock.elapsedRealtime();
                    do {
                        Thread.sleep(100);
                    } while (((SystemClock.elapsedRealtime() - time) < limit));
                } catch (InterruptedException e) {
                    Log.e(Constants.TAG, e.getMessage(), e);
                }
            }
        }
        return null;
    }

    @Override
    protected void onProgressUpdate(Object... values) {
        super.onProgressUpdate(values);
        if (mListener != null) {
            mListener.onPlayPoseTaskUpdate((String)values[0], (PoseModel)values[1],
                    (Integer)values[2], (Integer)values[3]);
        }
    }

    @Override
    protected void onPostExecute(Void result) {
        super.onPostExecute(result);
        if (mListener != null) {
            mListener.onPlayPoseTaskFinish();
        }
    }
}
