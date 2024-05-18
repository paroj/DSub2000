package github.paroj.dsub2000.view.compat;

import android.content.Context;
import android.os.Bundle;
import androidx.mediarouter.app.MediaRouteControllerDialog;
import androidx.mediarouter.app.MediaRouteControllerDialogFragment;

import github.paroj.dsub2000.util.ThemeUtil;

public class CustomMediaRouteControllerDialogFragment extends MediaRouteControllerDialogFragment {
	@Override
	public MediaRouteControllerDialog onCreateControllerDialog(Context context, Bundle savedInstanceState) {
		return new MediaRouteControllerDialog(context, ThemeUtil.getThemeRes(context));
	}
}
