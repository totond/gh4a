/*
 * Copyright 2011 Azwan Adli Abdullah
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gh4a.fragment;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.IdRes;
import android.support.v4.content.Loader;
import android.text.SpannableStringBuilder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gh4a.R;
import com.gh4a.activities.CollaboratorListActivity;
import com.gh4a.activities.ContributorListActivity;
import com.gh4a.activities.DownloadsActivity;
import com.gh4a.activities.ForkListActivity;
import com.gh4a.activities.IssueListActivity;
import com.gh4a.activities.ReleaseListActivity;
import com.gh4a.activities.RepositoryActivity;
import com.gh4a.activities.StargazerListActivity;
import com.gh4a.activities.UserActivity;
import com.gh4a.activities.WatcherListActivity;
import com.gh4a.activities.WikiListActivity;
import com.gh4a.loader.LoaderCallbacks;
import com.gh4a.loader.LoaderResult;
import com.gh4a.loader.PullRequestCountLoader;
import com.gh4a.loader.ReadmeLoader;
import com.gh4a.utils.ApiHelpers;
import com.gh4a.utils.HttpImageGetter;
import com.gh4a.utils.StringUtils;
import com.gh4a.utils.UiUtils;
import com.gh4a.widget.IntentSpan;
import com.gh4a.widget.OverviewRow;
import com.vdurmont.emoji.EmojiParser;

import org.eclipse.egit.github.core.Permissions;
import org.eclipse.egit.github.core.Repository;

public class RepositoryFragment extends LoadingFragmentBase implements OnClickListener {
    public static RepositoryFragment newInstance(Repository repository, String ref) {
        RepositoryFragment f = new RepositoryFragment();

        Bundle args = new Bundle();
        args.putSerializable("repo", repository);
        args.putString("ref", ref);
        f.setArguments(args);

        return f;
    }

    private static final int ID_LOADER_README = 0;
    private static final int ID_LOADER_PULL_REQUEST_COUNT = 1;
    private static final String STATE_KEY_IS_README_EXPANDED = "is_readme_expanded";
    private static final String STATE_KEY_IS_README_LOADED = "is_readme_loaded";

    private Repository mRepository;
    private View mContentView;
    private String mRef;
    private HttpImageGetter mImageGetter;
    private TextView mReadmeView;
    private View mLoadingView;
    private TextView mReadmeTitleView;
    private boolean mIsReadmeLoaded = false;
    private boolean mIsReadmeExpanded = false;

    private final LoaderCallbacks<String> mReadmeCallback = new LoaderCallbacks<String>(this) {
        @Override
        protected Loader<LoaderResult<String>> onCreateLoader() {
            mIsReadmeLoaded = false;
            return new ReadmeLoader(getActivity(), mRepository.getOwner().getLogin(),
                    mRepository.getName(), StringUtils.isBlank(mRef) ? mRepository.getDefaultBranch() : mRef);
        }
        @Override
        protected void onResultReady(String result) {
            new FillReadmeTask(mRepository.getId(), mReadmeView, mLoadingView, mImageGetter)
                    .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, result);
        }
    };

    private final LoaderCallbacks<Integer> mPullRequestsCallback = new LoaderCallbacks<Integer>(this) {
        @Override
        protected Loader<LoaderResult<Integer>> onCreateLoader() {
            return new PullRequestCountLoader(getActivity(), mRepository, ApiHelpers.IssueState.OPEN);
        }

        @Override
        protected void onResultReady(Integer result) {
            OverviewRow issuesRow = mContentView.findViewById(R.id.issues_row);
            int issueCount = mRepository.getOpenIssues() - result;
            issuesRow.setText(getResources().getQuantityString(R.plurals.issue, issueCount, issueCount));

            OverviewRow pullsRow = mContentView.findViewById(R.id.pulls_row);
            pullsRow.setText(getResources().getQuantityString(R.plurals.pull_request, result, result));
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mRepository = (Repository) getArguments().getSerializable("repo");
        mRef = getArguments().getString("ref");
    }

    @Override
    protected View onCreateContentView(LayoutInflater inflater, ViewGroup parent) {
        mContentView = inflater.inflate(R.layout.repository, parent, false);
        mReadmeView = mContentView.findViewById(R.id.readme);
        mLoadingView = mContentView.findViewById(R.id.pb_readme);
        mReadmeTitleView = mContentView.findViewById(R.id.readme_title);
        return mContentView;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mImageGetter.destroy();
        mImageGetter = null;
    }

    @Override
    public void onRefresh() {
        if (mReadmeView != null) {
            mReadmeView.setVisibility(View.GONE);
        }
        if (mLoadingView != null && mIsReadmeExpanded) {
            mLoadingView.setVisibility(View.VISIBLE);
        }
        if (mContentView != null) {
            OverviewRow issuesRow = mContentView.findViewById(R.id.issues_row);
            issuesRow.setText(null);
            OverviewRow pullsRow = mContentView.findViewById(R.id.pulls_row);
            pullsRow.setText(null);
        }
        if (mImageGetter != null) {
            mImageGetter.clearHtmlCache();
        }
        hideContentAndRestartLoaders(ID_LOADER_README, ID_LOADER_PULL_REQUEST_COUNT);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mImageGetter = new HttpImageGetter(getActivity());
        fillData();
        setContentShown(true);

        if (savedInstanceState != null) {
            mIsReadmeExpanded = savedInstanceState.getBoolean(STATE_KEY_IS_README_EXPANDED, false);
            mIsReadmeLoaded = savedInstanceState.getBoolean(STATE_KEY_IS_README_LOADED, false);
        }
        if (mIsReadmeExpanded || mIsReadmeLoaded) {
            getLoaderManager().initLoader(ID_LOADER_README, null, mReadmeCallback);
        }
        getLoaderManager().initLoader(ID_LOADER_PULL_REQUEST_COUNT, null, mPullRequestsCallback);

        updateReadmeVisibility();
    }

    @Override
    public void onResume() {
        super.onResume();
        mImageGetter.resume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mImageGetter.pause();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_KEY_IS_README_EXPANDED, mIsReadmeExpanded);
        outState.putBoolean(STATE_KEY_IS_README_LOADED, mIsReadmeLoaded);
    }

    public void setRef(String ref) {
        mRef = ref;
        getArguments().putString("ref", ref);

        // Reload readme
        if (getLoaderManager().getLoader(ID_LOADER_README) != null) {
            getLoaderManager().restartLoader(ID_LOADER_README, null, mReadmeCallback);
        }
        if (mReadmeView != null) {
            mReadmeView.setVisibility(View.GONE);
        }
        if (mLoadingView != null && mIsReadmeExpanded) {
            mLoadingView.setVisibility(View.VISIBLE);
        }
    }

    private void fillData() {
        TextView tvRepoName = mContentView.findViewById(R.id.tv_repo_name);
        SpannableStringBuilder repoName = new SpannableStringBuilder();
        repoName.append(mRepository.getOwner().getLogin());
        repoName.append("/");
        repoName.append(mRepository.getName());
        repoName.setSpan(new IntentSpan(tvRepoName.getContext()) {
            @Override
            protected Intent getIntent() {
                return UserActivity.makeIntent(getActivity(), mRepository.getOwner());
            }
        }, 0, mRepository.getOwner().getLogin().length(), 0);
        tvRepoName.setText(repoName);
        tvRepoName.setMovementMethod(UiUtils.CHECKING_LINK_METHOD);

        TextView tvParentRepo = mContentView.findViewById(R.id.tv_parent);
        if (mRepository.isFork() && mRepository.getParent() != null) {
            Repository parent = mRepository.getParent();
            tvParentRepo.setVisibility(View.VISIBLE);
            tvParentRepo.setText(getString(R.string.forked_from,
                    parent.getOwner().getLogin() + "/" + parent.getName()));
            tvParentRepo.setOnClickListener(this);
            tvParentRepo.setTag(parent);
        } else {
            tvParentRepo.setVisibility(View.GONE);
        }

        fillTextView(R.id.tv_desc, 0, mRepository.getDescription());
        fillTextView(R.id.tv_url, 0, !StringUtils.isBlank(mRepository.getHomepage())
                ? mRepository.getHomepage() : mRepository.getHtmlUrl());

        final String owner = mRepository.getOwner().getLogin();
        final String name = mRepository.getName();

        OverviewRow languageRow = mContentView.findViewById(R.id.language_row);
        languageRow.setVisibility(StringUtils.isBlank(mRepository.getLanguage())
                ? View.GONE : View.VISIBLE);
        languageRow.setText(getString(R.string.repo_language, mRepository.getLanguage()));

        OverviewRow issuesRow = mContentView.findViewById(R.id.issues_row);
        issuesRow.setVisibility(mRepository.isHasIssues() ? View.VISIBLE : View.GONE);
        issuesRow.setClickIntent(IssueListActivity.makeIntent(getActivity(), owner, name));

        OverviewRow pullsRow = mContentView.findViewById(R.id.pulls_row);
        pullsRow.setClickIntent(IssueListActivity.makeIntent(getActivity(), owner, name, true));

        OverviewRow forksRow = mContentView.findViewById(R.id.forks_row);
        forksRow.setText(getResources().getQuantityString(R.plurals.fork,
                mRepository.getForks(), mRepository.getForks()));
        forksRow.setClickIntent(ForkListActivity.makeIntent(getActivity(), owner, name));

        OverviewRow starsRow = mContentView.findViewById(R.id.stars_row);
        starsRow.setText(getResources().getQuantityString(R.plurals.star,
                mRepository.getStargazers(), mRepository.getStargazers()));
        starsRow.setClickIntent(StargazerListActivity.makeIntent(getActivity(), owner, name));

        OverviewRow watcherRow = mContentView.findViewById(R.id.watchers_row);
        watcherRow.setText(getResources().getQuantityString(R.plurals.watcher,
                mRepository.getWatchers(), mRepository.getWatchers()));
        watcherRow.setClickIntent(WatcherListActivity.makeIntent(getActivity(), owner, name));

        mContentView.findViewById(R.id.tv_contributors_label).setOnClickListener(this);
        mContentView.findViewById(R.id.other_info).setOnClickListener(this);
        mContentView.findViewById(R.id.tv_releases_label).setOnClickListener(this);
        mReadmeTitleView.setOnClickListener(this);

        Permissions permissions = mRepository.getPermissions();
        updateClickableLabel(R.id.tv_collaborators_label,
                permissions != null && permissions.hasPushAccess());
        updateClickableLabel(R.id.tv_downloads_label, mRepository.isHasDownloads());
        updateClickableLabel(R.id.tv_wiki_label, mRepository.isHasWiki());

        mContentView.findViewById(R.id.tv_private).setVisibility(
                mRepository.isPrivate() ? View.VISIBLE : View.GONE);

    }

    private void updateClickableLabel(int id, boolean enable) {
        View view = mContentView.findViewById(id);
        if (enable) {
            view.setOnClickListener(this);
            view.setVisibility(View.VISIBLE);
        } else {
            view.setVisibility(View.GONE);
        }
    }

    private void fillTextView(int id, int stringId, String text) {
        TextView view = mContentView.findViewById(id);

        if (!StringUtils.isBlank(text)) {
            if (stringId != 0) {
                view.setText(getString(stringId, text));
            } else {
                view.setText(EmojiParser.parseToUnicode(text));
            }
            view.setVisibility(View.VISIBLE);
        } else {
            view.setVisibility(View.GONE);
        }
    }

    public void updateStargazerCount(boolean starring) {
        if (starring) {
            mRepository.setStargazers(mRepository.getStargazers() + 1);
        } else {
            mRepository.setStargazers(mRepository.getStargazers() - 1);
        }

        OverviewRow starsRow = mContentView.findViewById(R.id.stars_row);
        starsRow.setText(getResources().getQuantityString(R.plurals.star,
                mRepository.getStargazers(), mRepository.getStargazers()));
    }

    public void updateWatcherCount(boolean watching) {
        if (watching) {
            mRepository.setWatchers(mRepository.getWatchers() + 1);
        } else {
            mRepository.setWatchers(mRepository.getWatchers() - 1);
        }

        OverviewRow watchersRow = mContentView.findViewById(R.id.watchers_row);
        watchersRow.setText(getResources().getQuantityString(R.plurals.watcher,
                mRepository.getWatchers(), mRepository.getWatchers()));
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();

        if (id == R.id.readme_title) {
            toggleReadmeExpanded();
            return;
        }

        String owner = mRepository.getOwner().getLogin();
        String name = mRepository.getName();
        Intent intent = null;

        if (id == R.id.tv_contributors_label) {
            intent = ContributorListActivity.makeIntent(getActivity(), owner, name);
        } else if (id == R.id.tv_collaborators_label) {
            intent = CollaboratorListActivity.makeIntent(getActivity(), owner, name);
        } else if (id == R.id.tv_wiki_label) {
            intent = WikiListActivity.makeIntent(getActivity(), owner, name, null);
        } else if (id == R.id.tv_downloads_label) {
            intent = DownloadsActivity.makeIntent(getActivity(), owner, name);
        } else if (id == R.id.tv_releases_label) {
            intent = ReleaseListActivity.makeIntent(getActivity(), owner, name);
        } else if (view.getTag() instanceof Repository) {
            intent = RepositoryActivity.makeIntent(getActivity(), (Repository) view.getTag());
        }

        if (intent != null) {
            startActivity(intent);
        }
    }

    private void toggleReadmeExpanded() {
        mIsReadmeExpanded = !mIsReadmeExpanded;

        if (mIsReadmeExpanded && !mIsReadmeLoaded) {
            getLoaderManager().initLoader(ID_LOADER_README, null, mReadmeCallback);
        }

        updateReadmeVisibility();
    }

    private void updateReadmeVisibility() {
        mReadmeView.setVisibility(mIsReadmeExpanded && mIsReadmeLoaded ? View.VISIBLE : View.GONE);
        mLoadingView.setVisibility(
                mIsReadmeExpanded && !mIsReadmeLoaded ? View.VISIBLE : View.GONE);

        int drawableAttr = mIsReadmeExpanded ? R.attr.dropUpArrowIcon : R.attr.dropDownArrowIcon;
        int drawableRes = UiUtils.resolveDrawable(getContext(), drawableAttr);
        mReadmeTitleView.setCompoundDrawablesWithIntrinsicBounds(0, 0, drawableRes, 0);
    }

    private class FillReadmeTask extends AsyncTask<String, Void, String> {
        private final Long mId;
        private final Context mContext;
        private final TextView mReadmeView;
        private final View mProgressView;
        private final HttpImageGetter mImageGetter;

        public FillReadmeTask(long id, TextView readmeView, View progressView,
                HttpImageGetter imageGetter) {
            mId = id;
            mContext = readmeView.getContext();
            mReadmeView = readmeView;
            mProgressView = progressView;
            mImageGetter = imageGetter;
        }

        @Override
        protected String doInBackground(String... params) {
            String readme = params[0];
            if (readme != null) {
                mImageGetter.encode(mContext, mId, readme);
            }
            return readme;
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                mReadmeView.setMovementMethod(UiUtils.CHECKING_LINK_METHOD);
                mImageGetter.bind(mReadmeView, result, mId);
            } else {
                mReadmeView.setText(R.string.repo_no_readme);
                mReadmeView.setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
            }
            mReadmeView.setVisibility(mIsReadmeExpanded ? View.VISIBLE : View.GONE);
            mProgressView.setVisibility(View.GONE);
            mIsReadmeLoaded = true;
        }
    }
}