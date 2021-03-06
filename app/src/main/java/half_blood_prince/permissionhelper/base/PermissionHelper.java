package half_blood_prince.permissionhelper.base;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PermissionHelper class helps to keep the code for requesting permisssion from android M in one place
 *
 * @author Sridhar
 */
public abstract class PermissionHelper {

    /**
     * Model class used to keep the required attributes about the permission
     */
    public static class PermissionModel {
        /**
         * Permission to request
         */
        String permission;

        /**
         * Title of the dialog box to show when the user denied the permission previously
         */
        String rationaleTitle;

        /**
         * Body of the dialog box to show when the user denied the permission previously
         */
        String rationaleMessage;

        /**
         * Positive button text
         * Default text is okay
         */
        String posBtnText;

        /**
         * Negative button text
         * Default text is Cancel
         */
        String negBtnText;

        /**
         * Constructor initialized with the permission, rationaleTitle, rationaleMessage, and default
         * values for posBtnText and negBtnText
         *
         * @param permission       permission to request
         * @param rationaleTitle   dialog title to show if user denied the permission previously
         * @param rationaleMessage dialog content to show if user denied the permission previously
         */
        public PermissionModel(String permission, String rationaleTitle, String rationaleMessage) {
            this(permission, rationaleTitle, rationaleMessage, "Okay", "Cancel");
        }

        /**
         * Constructor initialized with the permission, rationaleTitle, rationaleMessage,posBtnText.
         * and negBtnText
         *
         * @param permission       permission to request
         * @param rationaleTitle   dialog title to show if user denied the permission previously
         * @param rationaleMessage dialog content to show if user denied the permission previously
         * @param posBtnText       Positive button text
         * @param negBtnText       Negative button text
         */
        public PermissionModel(String permission, String rationaleTitle,
                               String rationaleMessage, String posBtnText, String negBtnText) {
            this.permission = permission;
            this.rationaleTitle = rationaleTitle;
            this.rationaleMessage = rationaleMessage;
            this.posBtnText = posBtnText;
            this.negBtnText = negBtnText;
        }
    }

    /**
     * Activity reference
     */
    private Activity activity;

    /**
     * LinkedHashMap to hold permission to check and request access with their corresponding id
     */
    private LinkedHashMap<Integer, PermissionModel> permissionMap;

    /**
     * Queue to process the pending permission
     */
    private Deque<Integer> queue = new ArrayDeque<>();

    private ArrayList<Integer> grantedPermission = new ArrayList<>();

    private ArrayList<Integer> deniedPermission = new ArrayList<>();

    private ArrayList<Integer> completelyDeniedPermission = new ArrayList<>();

    private boolean isGroupOfPermissionRequested;

    /**
     * Constructor used to initialize this class object
     *
     * @param activity      activity reference
     * @param permissionMap map contains permission to check and request with their corresponding id
     */
    protected PermissionHelper(@NonNull Activity activity,
                               @NonNull Map<Integer, PermissionModel> permissionMap) {
        this.activity = activity;
        this.permissionMap = (LinkedHashMap<Integer, PermissionModel>) permissionMap;

    }

    /**
     * This method initialize the queue and start the checking process
     */
    public final void startCheckingPermission() {
        isGroupOfPermissionRequested = permissionMap.keySet().size() > 1;
        queue.addAll(permissionMap.keySet());
        if (!queue.isEmpty())
            checkPermission(queue.peek());
    }

    /**
     * This method checks whether this per
     *
     * @param permissionID unique permission id pointing some permission model object in permissionMap
     * @see PermissionModel
     * @see this.permissionMap
     */
    private void checkPermission(final int permissionID) {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            dispatchGrantedPermission(permissionID);
            return;
        }

        String permission = permissionMap.get(permissionID).permission;

        if (isPermitted(permission)) {
            dispatchGrantedPermission(permissionID);
            return;
        }

        if (doIHaveToExplain(permission)) {
            // show msg , attach callback
            explainAboutPermission(activity, permissionID, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (which == AlertDialog.BUTTON_POSITIVE)
                        requestPermission(permissionID);
                    else
                        dispatchDeniedPermission(permissionID);

                    dialog.dismiss();
                }
            });
        } else {
            requestPermission(permissionID);
        }

    }

    /**
     * This method check whether the requested permission has been granted access earlier ot not
     *
     * @param permission permission to check
     * @return true if the access is granted for the requested permission
     */
    private boolean isPermitted(String permission) {
        return ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * This method request for the permission
     * <p>
     * Note * As of now only one permission can be requested , even though android support sending
     * array of permission to request.i am not using that feature
     *
     * @param permissionID corresponding permissionID for the permission to request
     */
    private void requestPermission(int permissionID) {
        String permission = permissionMap.get(permissionID).permission;
        if (null != permission) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{permission}
                    , permissionID);
            permissionRequested(permissionID);
        }
    }

    /**
     * This method must be called from the activty side after onRequestPermissionsResult in the activity
     *
     * @param requestCode  requestCode is nothing but the corresponding permission id
     * @param permissions  string array of permission (not using this right now )
     * @param grantResults grantResults
     */
    public final void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                                 @NonNull int[] grantResults) {

        String permission = permissionMap.get(requestCode).permission;
        if (null == permission)
            permission = "";

        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED
                && (permissions.length > 0 && permission.equals(permissions[0]))
                ) {
            dispatchGrantedPermission(requestCode);
        } else handleDeny(requestCode);

    }

    /**
     * Removes the previous permission ID from the queue and look for the next element to begin the
     * checking/requesting process
     */
    private void checkNextPermission() {

        queue.poll();

        if (!queue.isEmpty()) {
            checkPermission(queue.peek());
        } else {
            if (isGroupOfPermissionRequested) {
                dispatchGroupResult();
            }
        }
    }

    /**
     * This method invoked when the user denied the permission,
     * will determine whether the permission if completely denied or currently denied
     *
     * @param permissionID id of the permission to check
     */
    private void handleDeny(int permissionID) {
        if (doIHaveToExplain(permissionMap.get(permissionID).permission)) {
            dispatchDeniedPermission(permissionID);
        } else {
            dispatchCompletelyDeniedPermission(permissionID);
        }
    }

    /**
     * Check whether we have to clarify the user about the permission we are requesting
     * why we need that permission
     *
     * @param permission permission to check whether the explanation needed to show
     * @return true if we need to explain about the permission false otherwise
     */
    private boolean doIHaveToExplain(String permission) {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission);
    }

    /**
     * This method show the dialog which contains the information about why we need this permission and etc
     *
     * @param activity        activity reference
     * @param permissionID    permission id mapped to the corresponding permission
     * @param onClickListener listener to delegate the dialog click events back to the logic
     */
    private void explainAboutPermission(Activity activity, int permissionID,
                                        DialogInterface.OnClickListener onClickListener) {

        PermissionModel permissionModel = permissionMap.get(permissionID);

        AlertDialog dialog = new AlertDialog.Builder(activity).create();
        dialog.setTitle(permissionModel.rationaleTitle);
        dialog.setMessage(permissionModel.rationaleMessage);
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, permissionModel.posBtnText, onClickListener);
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, permissionModel.negBtnText, onClickListener);

        dialog.setCancelable(false);

        dialog.show();
    }

    private void dispatchGroupResult() {
        int size = grantedPermission.size() +
                deniedPermission.size() + completelyDeniedPermission.size();
        int[] requestedIds = new int[size];
        int[] results = new int[size];

        size = grantedPermission.size();
        for (int i = 0; i < size; i++) {
            requestedIds[i] = grantedPermission.get(i);
            results[i] = PackageManager.PERMISSION_GRANTED;
        }
        size = deniedPermission.size();
        for (int i = 0; i < size; i++) {
            requestedIds[i] = deniedPermission.get(i);
            results[i] = PackageManager.PERMISSION_DENIED;
        }
        size = completelyDeniedPermission.size();
        for (int i = 0; i < size; i++) {
            requestedIds[i] = completelyDeniedPermission.get(i);
            results[i] = PackageManager.PERMISSION_DENIED;
        }

        onGroupOfPermissionRequestResult(requestedIds, results);
    }

    private void dispatchGrantedPermission(int permissionId) {
        if (isGroupOfPermissionRequested) {
            grantedPermission.add(permissionId);
            checkNextPermission();
        } else
            permissionGranted(permissionId);
    }

    private void dispatchDeniedPermission(int permissionId) {
        if (isGroupOfPermissionRequested) {
            deniedPermission.add(permissionId);
            checkNextPermission();
        } else permissionDenied(permissionId);
    }

    private void dispatchCompletelyDeniedPermission(int permissionId) {
        if (isGroupOfPermissionRequested) {
            completelyDeniedPermission.add(permissionId);
            checkNextPermission();
        } else permissionDeniedCompletely(permissionId);
    }

    /**
     * Callback method to inform about the permission has been granted
     *
     * @param permissionID id mapped to PermissionModel
     * @see PermissionModel
     */
    protected void permissionGranted(int permissionID) {

    }

    /**
     * Callback method to inform about the permission has been denied
     *
     * @param permissionID id mapped to PermissionModel
     * @see PermissionModel
     */
    protected void permissionDenied(int permissionID) {

    }

    /**
     * Callback method to inform about the permission has been denied completely ,
     * and android will no longer display that popup saying this app needs this permission
     *
     * @param permissionID id mapped to PermissionModel
     * @see PermissionModel
     */
    protected void permissionDeniedCompletely(int permissionID) {

    }

    /**
     * todo Remove this method
     * <p>
     * Callback method to inform about the permission has been requested
     *
     * @param permissionID id mapped to PermissionModel
     * @see PermissionModel
     */
    protected void permissionRequested(int permissionID) {

    }

    protected void onGroupOfPermissionRequestResult(int[] requestedIds, int[] grantResults) {

    }


}
