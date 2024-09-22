package tech.linjiang.pandora.ui.fragment;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import tech.linjiang.pandora.core.R;
import tech.linjiang.pandora.ui.connector.SimpleOnActionExpandListener;
import tech.linjiang.pandora.ui.connector.SimpleOnQueryTextListener;
import tech.linjiang.pandora.ui.item.RouteItem;
import tech.linjiang.pandora.ui.item.TitleItem;
import tech.linjiang.pandora.ui.recyclerview.BaseItem;
import tech.linjiang.pandora.ui.recyclerview.UniversalAdapter;
import tech.linjiang.pandora.util.Config;
import tech.linjiang.pandora.util.Utils;

/**
 * Created by linjiang on 2019/1/13.
 */

public class RouteFragment extends BaseListFragment {

    @Override
    protected boolean enableSwipeBack() {
        return false;
    }

    private final ArrayList<RouteItem> routeItemArrayList = new ArrayList<>();
    private ArrayList<String> historyList = new ArrayList<>();

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        getToolbar().setTitle(R.string.pd_name_navigate);
        getToolbar().getMenu().add(-1, R.id.pd_menu_id_2, 0, R.string.pd_name_search)
                .setActionView(new SearchView(requireContext()))
                .setIcon(R.drawable.pd_search)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        initSearchView();
        readHistory();
        List<String> activities = Utils.getActivities();
        for (int i = 0; i < activities.size(); i++) {
            routeItemArrayList.add(new RouteItem(activities.get(i), callback));
        }
        updateList(routeItemArrayList);
        getAdapter().setListener(new UniversalAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(int position, BaseItem item) {
                if (position == 0 && item instanceof TitleItem) {
                    getAdapter().removeItems(0, historyList.size() + 2);
                    historyList.clear();
                    Config.updateRouteLaunchHistory("");
                }
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CODE1 && resultCode == Activity.RESULT_OK) {
            go(data);
        }
    }

    private final RouteItem.Callback callback = new RouteItem.Callback() {
        @Override
        public void onClick(String simpleName, String clazz, boolean needParam) {
            if (needParam) {
                Bundle bundle = new Bundle();
                bundle.putString(PARAM1, simpleName);
                bundle.putString(PARAM2, clazz);
                launch(RouteParamFragment.class, bundle, CODE1);
                logHistory(clazz);
                return;
            }
            try {
                Intent intent = new Intent(getContext(), Class.forName(clazz));
                go(intent);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    };

    private void go(Intent intent) {
        logHistory(intent.getComponent().getClassName());
        startActivity(intent);
        getActivity().finish();
    }

    private void initSearchView() {
        MenuItem menuItem = getToolbar().getMenu().findItem(R.id.pd_menu_id_2);
        SearchView searchView = (SearchView) menuItem.getActionView();
        searchView.setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        searchView.setOnQueryTextListener(new SimpleOnQueryTextListener() {
            @Override
            public boolean onQueryTextChange(String newText) {
                filter(newText);
                return true;
            }

            @Override
            public boolean onQueryTextSubmit(String query) {
                closeSoftInput();
                filter(query);
                return true;
            }
        });
        SimpleOnActionExpandListener.bind(menuItem, new SimpleOnActionExpandListener() {
            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                updateList(routeItemArrayList);
                return true;
            }
        });
    }

    private void filter(String keyWord) {
        if (TextUtils.isEmpty(keyWord)) {
            updateList(routeItemArrayList);
            return;
        }
        ArrayList<RouteItem> newList = new ArrayList<>();
        for (int i = 0; i < routeItemArrayList.size(); i++) {
            RouteItem routeItem = routeItemArrayList.get(i);
            if (routeItem.data.toLowerCase().contains(keyWord.toLowerCase())) {
                newList.add(routeItem);
            }
        }
        updateList(newList);
    }

    private void updateList(ArrayList<RouteItem> list) {
        if (historyList.isEmpty()) {
            getAdapter().setItems(list);
        } else {
            List<BaseItem> data = new ArrayList<>();
            data.add(new TitleItem("历史（点击清空）"));
            for (String s : historyList) {
                data.add(new RouteItem(s, callback));
            }
            data.add(new TitleItem("应用内页面"));
            data.addAll(list);
            getAdapter().setItems(data);
        }
    }

    private void readHistory() {
        String history = Config.getRouteLaunchHistory();
        if (TextUtils.isEmpty(history)) return;
        String[] split = history.split(",");
        historyList.addAll(Arrays.asList(split));
    }

    private void logHistory(String clazz) {
        List<BaseItem> items = getAdapter().getItems();
        int titleSize = 0;
        int existsClazzIndex = -1;
        RouteItem existsClazzItem = null;
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            if (titleSize != 2 && item instanceof TitleItem) {
                titleSize++;
            }
            if (titleSize == 1 && item instanceof RouteItem) {
                RouteItem route = (RouteItem) item;
                if (Objects.equals(route.data, clazz)) {
                    existsClazzIndex = i;
                    existsClazzItem = route;
                }
            }
        }
        historyList.remove(clazz);
        historyList.add(0, clazz);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < historyList.size(); i++) {
            sb.append(historyList.get(i))
                    .append(",");
        }
        Config.updateRouteLaunchHistory(sb.deleteCharAt(sb.length() - 1).toString());
        if (existsClazzIndex > 0) {
            getAdapter().removeItem(existsClazzIndex);
            getAdapter().insertItem(existsClazzItem, 1);
        } else {
            getAdapter().insertItem(new RouteItem(clazz, callback));
        }
    }
}
