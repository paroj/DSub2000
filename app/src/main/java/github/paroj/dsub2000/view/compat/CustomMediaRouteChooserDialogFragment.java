package github.paroj.dsub2000.view.compat;

import android.content.Context;
import android.os.Bundle;
import androidx.mediarouter.app.MediaRouteChooserDialog;
import androidx.mediarouter.app.MediaRouteChooserDialogFragment;

import github.paroj.dsub2000.util.ThemeUtil;

public class CustomMediaRouteChooserDialogFragment extends MediaRouteChooserDialogFragment {
	@Override
	public MediaRouteChooserDialog onCreateChooserDialog(Context context, Bundle savedInstanceState) {
		return new MediaRouteChooserDialog(context, ThemeUtil.getThemeRes(context));
	}
}
