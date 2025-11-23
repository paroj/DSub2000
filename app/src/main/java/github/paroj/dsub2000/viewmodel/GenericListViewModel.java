package github.paroj.dsub2000.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;

public class GenericListViewModel<T> extends ViewModel {

    private final MutableLiveData<List<T>> data = new MutableLiveData<>();

    public LiveData<List<T>> getData() {
        return data;
    }

    public void setData(List<T> newData) {
        data.setValue(newData);
    }

}