package tech.linjiang.pandora.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Pair;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import tech.linjiang.pandora.cache.Content;
import tech.linjiang.pandora.cache.Summary;
import tech.linjiang.pandora.core.R;
import tech.linjiang.pandora.ui.item.ExceptionItem;
import tech.linjiang.pandora.ui.item.KeyValueItem;
import tech.linjiang.pandora.ui.item.TitleItem;
import tech.linjiang.pandora.ui.recyclerview.BaseItem;
import tech.linjiang.pandora.ui.recyclerview.UniversalAdapter;
import tech.linjiang.pandora.util.FileUtil;
import tech.linjiang.pandora.util.FormatUtil;
import tech.linjiang.pandora.util.SimpleTask;
import tech.linjiang.pandora.util.Utils;

/**
 * Created by linjiang on 2018/6/23.
 */

public class NetSummaryFragment extends BaseListFragment {

    private Summary originData;
    private Content cacheContent;

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final long id = getArguments().getLong(PARAM1);
        loadData(id);
        getAdapter().setListener(new UniversalAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(int position, BaseItem item) {
                if (item instanceof KeyValueItem) {
                    String tag = (String) item.getTag();
                    if (!TextUtils.isEmpty(tag)) {
                        Bundle bundle = new Bundle();
                        if (PARAM1.equals(tag)) {
                            bundle.putBoolean(PARAM1, false);
                            bundle.putString(PARAM3, originData.request_content_type);
                        } else if (PARAM2.equals(tag)) {
                            if (!TextUtils.isEmpty(originData.response_content_type)
                                    && originData.response_content_type.contains("image")) {
                                tryOpen(originData.id);
                                return;
                            }
                            bundle.putBoolean(PARAM1, true);
                            bundle.putString(PARAM3, originData.response_content_type);
                        }
                        if (!bundle.isEmpty()) {
                            bundle.putLong(PARAM2, id);
                            launch(NetContentFragment.class, bundle);
                        }
                    } else {
                        String value = ((String[]) item.data)[1];
                        if (!TextUtils.isEmpty(value)) {
                            Utils.copy2ClipBoard(value);
                        }
                    }
                }
            }
        });
        getToolbar().getMenu().add(-1, 0, 0, R.string.pd_name_copy_value);
        getToolbar().getMenu().add(-1, 0, 1, R.string.pd_name_share);
        getToolbar().setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(final MenuItem item) {
                if (originData == null) return false;
                final int order = item.getOrder();
                if (cacheContent == null &&
                        (originData.status == Summary.Status.ERROR
                                || originData.request_size > 0
                                || originData.response_size > 0
                        )
                ) {
                    queryContent(id, new Runnable() {
                        @Override
                        public void run() {
                            final String msg = buildStr();
                            if (order == 0) {
                                Utils.copy2ClipBoard(msg);
                            } else if (order == 1) {
                                saveAsFileAndShare(msg);
                            }
                        }
                    });
                } else {
                    final String msg = buildStr();
                    if (order == 0) {
                        Utils.copy2ClipBoard(msg);
                    } else if (order == 1) {
                        saveAsFileAndShare(msg);
                    }
                }

                return true;
            }
        });
    }

    private String buildStr() {
        StringBuilder sb = new StringBuilder();
        StringBuilder n = new StringBuilder("\n");
        StringBuilder t = new StringBuilder("\t");
        String requestContentType = originData.request_content_type;
        String responseContentType = originData.response_content_type;
        sb.append("░░░░░░░░░░░░░░ Request ░░░░░░░░░░░░░░").append(n)
                .append("URL: ").append(originData.url).append(n)
                .append("Method: ").append(originData.method).append(n)
                .append("StartTime: ").append(Utils.millis2String(originData.start_time)).append(n)
                .append("EndTime: ").append(Utils.millis2String(originData.end_time)).append(n)
                .append("Method: ").append(originData.method).append(n)
                .append("Content-Type: ").append(requestContentType).append(n)
                .append("Body size: ").append(Utils.formatSize(originData.request_size)).append(n);
        addHeaderStr(originData.request_header, sb);
        if (cacheContent != null
                && cacheContent.requestBody != null
                && isContentTypeValid(requestContentType)
        ) {
            sb.append("Body:").append(n)
                    .append(cacheContent.requestBody).append(n);
        }

        sb.append(n);
        sb.append("░░░░░░░░░░░░░░ Response ░░░░░░░░░░░░░░").append(n)
                .append("Status: ").append(originData.code).append(n)
                .append("Duration: ").append(Utils.formatDuration(originData.end_time - originData.start_time)).append(n)
                .append("Content-Type: ").append(responseContentType).append(n)
                .append("Body size: ").append(Utils.formatSize(originData.response_size)).append(n);
        addHeaderStr(originData.response_header, sb);
        if (cacheContent != null
                && cacheContent.responseBody != null
                && isContentTypeValid(responseContentType)
        ) {
            sb.append("Body:").append(n)
                    .append(cacheContent.responseBody).append(n);
        }

        return sb.toString();
    }

    private boolean isContentTypeValid(String type) {
        return type == null || type.contains("json") || type.contains("text");
    }

    private void addHeaderStr(List<Pair<String, String>> header, StringBuilder sb) {
        StringBuilder n = new StringBuilder("\n");
        StringBuilder t = new StringBuilder("\t");
        for (int i = 0; i < header.size(); i++) {
            Pair<String, String> pair = header.get(i);
            if (i == 0) {
                sb.append("Headers:").append(n);
            }
            sb.append(t).append(pair.first).append(": ").append(pair.second).append(n);
        }
    }

    private void queryContent(final long id, final Runnable runnable) {
        showLoading();
        new SimpleTask<>(new SimpleTask.Callback<Void, Content>() {
            @Override
            public Content doInBackground(Void[] params) {
                return Content.query(id);
            }

            @Override
            public void onPostExecute(Content result) {
                hideLoading();
                cacheContent = result;
                if (runnable != null) {
                    runnable.run();
                }
            }
        }).execute();
    }

    private void loadData(final long id) {
        showLoading();
        new SimpleTask<>(new SimpleTask.Callback<Void, Summary>() {
            @Override
            public Summary doInBackground(Void[] params) {
                Summary summary = Summary.query(id);
                summary.request_header = FormatUtil.parseHeaders(summary.requestHeader);
                summary.response_header = FormatUtil.parseHeaders(summary.responseHeader);
                return summary;
            }

            @Override
            public void onPostExecute(Summary summary) {
                hideLoading();
                if (summary == null) {
                    showError(null);
                    return;
                }
                originData = summary;
                getToolbar().setTitle(summary.path);
                getToolbar().setSubtitle(String.valueOf(summary.code == 0 ? "- -" : summary.code));

                List<BaseItem> data = new ArrayList<>();

                if (summary.status == Summary.Status.ERROR) {
                    Content content = Content.query(id);
                    data.add(new ExceptionItem(content.responseBody));
                }

                data.add(new TitleItem("GENERAL"));
                data.add(new KeyValueItem(Utils.newArray("url", summary.url)));
                data.add(new KeyValueItem(Utils.newArray("path", summary.path)));
                data.add(new KeyValueItem(Utils.newArray("host", summary.host)));
                data.add(new KeyValueItem(Utils.newArray("method", summary.method)));
                data.add(new KeyValueItem(Utils.newArray("protocol", summary.protocol)));
                data.add(new KeyValueItem(Utils.newArray("ssl", summary.ssl)));
                data.add(new KeyValueItem(Utils.newArray("start_time", Utils.millis2String(summary.start_time))));
                data.add(new KeyValueItem(Utils.newArray("end_time", Utils.millis2String(summary.end_time))));
                data.add(new KeyValueItem(Utils.newArray("req content-type", summary.request_content_type)));
                data.add(new KeyValueItem(Utils.newArray("res content-type", summary.response_content_type)));
                data.add(new KeyValueItem(Utils.newArray("request_size", Utils.formatSize(summary.request_size))));
                data.add(new KeyValueItem(Utils.newArray("response_size", Utils.formatSize(summary.response_size))));

//                if (!TextUtils.isEmpty(summary.query)) {
//                    data.add(new TitleItem(""));
//                    data.add(new KeyValueItem(Utils.newArray("query", summary.query)));
//                }

                data.add(new TitleItem("BODY"));
                KeyValueItem request = new KeyValueItem(Utils.newArray("request body", "tap to view"), false, true);
                request.setTag(PARAM1);
                data.add(request);
                if (summary.status == 2) {
                    KeyValueItem response = new KeyValueItem(Utils.newArray("response body", "tap to view"), false, true);
                    response.setTag(PARAM2);
                    data.add(response);
                }


                if (Utils.isNotEmpty(summary.request_header)) {
                    data.add(new TitleItem("REQUEST HEADER"));
                    for (Pair<String, String> pair : summary.request_header) {
                        data.add(new KeyValueItem(Utils.newArray(pair.first, pair.second)));
                    }
                }

                if (Utils.isNotEmpty(summary.response_header)) {
                    data.add(new TitleItem("RESPONSE HEADER"));
                    for (Pair<String, String> pair : summary.response_header) {
                        data.add(new KeyValueItem(Utils.newArray(pair.first, pair.second)));
                    }
                }

                getAdapter().setItems(data);
            }
        }).execute();
    }

    private void tryOpen(final long id) {
        new SimpleTask<>(new SimpleTask.Callback<Void, String>() {
            @Override
            public String doInBackground(Void[] params) {
                return Content.query(id).responseBody;
            }

            @Override
            public void onPostExecute(String result) {
                if (TextUtils.isEmpty(result)) {
                    Utils.toast(R.string.pd_failed);
                    return;
                }
                tryOpenInternal(result);
            }
        }).execute();
    }

    private void tryOpenInternal(String path) {
        new SimpleTask<>(new SimpleTask.Callback<File, Intent>() {
            @Override
            public Intent doInBackground(File[] params) {
                String result = FileUtil.fileCopy2Tmp(params[0]);
                if (!TextUtils.isEmpty(result)) {
                    return FileUtil.getFileIntent(result, "image/*");
                }
                return null;
            }

            @Override
            public void onPostExecute(Intent result) {
                hideLoading();
                if (result != null) {
                    try {
                        startActivity(result);
                    } catch (Throwable t) {
                        t.printStackTrace();
                        Utils.toast(t.getMessage());
                    }
                } else {
                    Utils.toast(R.string.pd_not_support);
                }
            }
        }).execute(new File(path));
        showLoading();
    }

    private void saveAsFileAndShare(String msg) {
        showLoading();
        new SimpleTask<>(new SimpleTask.Callback<String, Intent>() {
            @Override
            public Intent doInBackground(String[] params) {
                String path = FileUtil.saveFile(params[0].getBytes(), "netLog", "txt");
                String newPath = FileUtil.fileCopy2Tmp(new File(path));
                if (!TextUtils.isEmpty(newPath)) {
                    return FileUtil.getFileIntent(newPath);
                }
                return null;
            }

            @Override
            public void onPostExecute(Intent result) {
                hideLoading();
                if (result != null) {
                    try {
                        startActivity(result);
                    } catch (Throwable t) {
                        t.printStackTrace();
                        Utils.toast(t.getMessage());
                    }
                } else {
                    Utils.toast(R.string.pd_failed);
                }

            }
        }).execute(msg);
    }
}
