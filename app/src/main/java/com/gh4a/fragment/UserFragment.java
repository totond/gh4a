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

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.IdRes;
import android.support.annotation.PluralsRes;
import android.support.v4.content.Loader;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gh4a.BackgroundTask;
import com.gh4a.Gh4Application;
import com.gh4a.R;
import com.gh4a.activities.FollowerFollowingListActivity;
import com.gh4a.activities.GistListActivity;
import com.gh4a.activities.OrganizationMemberListActivity;
import com.gh4a.activities.RepositoryActivity;
import com.gh4a.activities.RepositoryListActivity;
import com.gh4a.activities.UserActivity;
import com.gh4a.loader.IsFollowingUserLoader;
import com.gh4a.loader.LoaderCallbacks;
import com.gh4a.loader.LoaderResult;
import com.gh4a.loader.OrganizationListLoader;
import com.gh4a.loader.OrganizationMemberListLoader;
import com.gh4a.loader.RepositoryListLoader;
import com.gh4a.loader.UserLoader;
import com.gh4a.utils.ApiHelpers;
import com.gh4a.utils.AvatarHandler;
import com.gh4a.utils.StringUtils;
import com.gh4a.widget.OverviewRow;

import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.User;
import org.eclipse.egit.github.core.service.UserService;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserFragment extends LoadingFragmentBase implements
        OverviewRow.OnIconClickListener, View.OnClickListener {
    public static UserFragment newInstance(String login) {
        UserFragment f = new UserFragment();

        Bundle args = new Bundle();
        args.putString("login", login);
        f.setArguments(args);

        return f;
    }

    private String mUserLogin;
    private User mUser;
    private View mContentView;
    private OverviewRow mFollowersRow;
    private Boolean mIsFollowing;
    private boolean mIsSelf;

    private final LoaderCallbacks<User> mUserCallback = new LoaderCallbacks<User>(this) {
        @Override
        protected Loader<LoaderResult<User>> onCreateLoader() {
            return new UserLoader(getActivity(), mUserLogin);
        }
        @Override
        protected void onResultReady(User result) {
            mUser = result;
            fillData();
            setContentShown(true);
            getActivity().invalidateOptionsMenu();
        }
    };

    private final LoaderCallbacks<Collection<Repository>> mRepoListCallback =
            new LoaderCallbacks<Collection<Repository>>(this) {
        @Override
        protected Loader<LoaderResult<Collection<Repository>>> onCreateLoader() {
            Map<String, String> filterData = new HashMap<>();
            filterData.put("sort", "pushed");
            filterData.put("affiliation", "owner,collaborator");
            return new RepositoryListLoader(getActivity(), mUserLogin,
                    mUser.getType(), filterData, 5);
        }
        @Override
        protected void onResultReady(Collection<Repository> result) {
            fillTopRepos(result);
        }
    };

    private final LoaderCallbacks<List<User>> mOrganizationCallback = new LoaderCallbacks<List<User>>(this) {
        @Override
        protected Loader<LoaderResult<List<User>>> onCreateLoader() {
            return new OrganizationListLoader(getActivity(), mUserLogin);
        }
        @Override
        protected void onResultReady(List<User> result) {
            fillOrganizations(result);
        }
    };

    private final LoaderCallbacks<Boolean> mIsFollowingCallback = new LoaderCallbacks<Boolean>(this) {
        @Override
        protected Loader<LoaderResult<Boolean>> onCreateLoader() {
            return new IsFollowingUserLoader(getActivity(), mUserLogin);
        }
        @Override
        protected void onResultReady(Boolean result) {
            mIsFollowing = result;
            updateFollowingUi();
            getLoaderManager().destroyLoader(3);
        }
    };

    private final LoaderCallbacks<List<User>> mOrganizationMemberCallback = new LoaderCallbacks<List<User>>(this) {
        @Override
        protected Loader<LoaderResult<List<User>>> onCreateLoader() {
            return new OrganizationMemberListLoader(getActivity(), mUserLogin);
        }

        @Override
        protected void onResultReady(List<User> result) {
            OverviewRow membersRow = mContentView.findViewById(R.id.members_row);
            membersRow.setText(getResources().getQuantityString(R.plurals.member,
                    result.size(), result.size()));
            getLoaderManager().destroyLoader(4);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mUserLogin = getArguments().getString("login");
        mIsSelf = ApiHelpers.loginEquals(mUserLogin, Gh4Application.get().getAuthLogin());
        setHasOptionsMenu(true);
    }

    @Override
    protected View onCreateContentView(LayoutInflater inflater, ViewGroup parent) {
        mContentView = inflater.inflate(R.layout.user, parent, false);
        return mContentView;
    }

    @Override
    public void onRefresh() {
        mUser = null;
        if (mFollowersRow != null && mIsFollowing != null) {
            mFollowersRow.setText(null);
        }
        mIsFollowing = null;
        if (mContentView != null) {
            fillOrganizations(null);
            fillTopRepos(null);
        }
        hideContentAndRestartLoaders(0, 3);
        getActivity().invalidateOptionsMenu();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        setContentShown(false);

        getLoaderManager().initLoader(0, null, mUserCallback);
    }

    private boolean canFollowUser() {
        if (mIsSelf) {
            return false;
        }
        if (!Gh4Application.get().isAuthorized()) {
            return false;
        }
        return mUser != null && ApiHelpers.UserType.USER.equals(mUser.getType());
    }

    private void fillData() {
        ImageView gravatar = mContentView.findViewById(R.id.iv_gravatar);
        AvatarHandler.assignAvatar(gravatar, mUser);

        OverviewRow joinDateRow = mContentView.findViewById(R.id.join_date_row);
        if (mUser.getCreatedAt() != null) {
            joinDateRow.setText(getString(R.string.user_created_at,
                    DateFormat.getMediumDateFormat(getActivity()).format(mUser.getCreatedAt())));
            joinDateRow.setVisibility(View.VISIBLE);
        } else {
            joinDateRow.setVisibility(View.GONE);
        }

        boolean isUser = ApiHelpers.UserType.USER.equals(mUser.getType());

        OverviewRow membersRow = mContentView.findViewById(R.id.members_row);
        membersRow.setVisibility(isUser ? View.GONE : View.VISIBLE);
        if (!isUser) {
            membersRow.setClickIntent(OrganizationMemberListActivity.makeIntent(getActivity(), mUserLogin));
            getLoaderManager().initLoader(4, null, mOrganizationMemberCallback);
        }

        mFollowersRow = mContentView.findViewById(R.id.followers_row);
        OverviewRow followingRow = mContentView.findViewById(R.id.following_row);

        mFollowersRow.setVisibility(isUser ? View.VISIBLE : View.GONE);
        followingRow.setVisibility(isUser ? View.VISIBLE : View.GONE);
        if (isUser) {
            if (canFollowUser()) {
                getLoaderManager().initLoader(3, null, mIsFollowingCallback);
                mFollowersRow.setIconClickListener(this);
            } else {
                updateFollowingUi();
                mFollowersRow.setIconClickListener(null);
            }
            mFollowersRow.setClickIntent(
                    FollowerFollowingListActivity.makeIntent(getActivity(), mUserLogin, true));
            followingRow.setText(getResources().getQuantityString(R.plurals.following,
                    mUser.getFollowing(), mUser.getFollowing()));
            followingRow.setClickIntent(
                    FollowerFollowingListActivity.makeIntent(getActivity(), mUserLogin, false));
        }

        OverviewRow gistsRow = mContentView.findViewById(R.id.gists_row);
        gistsRow.setVisibility(isUser ? View.VISIBLE : View.GONE);
        if (isUser) {
            int totalCount = mUser.getPublicGists() + mUser.getPrivateGists();
            gistsRow.setText(getResources().getQuantityString(R.plurals.gist, totalCount, totalCount));
            gistsRow.setClickIntent(GistListActivity.makeIntent(getActivity(), mUserLogin));
        }

        OverviewRow reposRow = mContentView.findViewById(R.id.repos_row);
        int repoCount = mUser.getTotalPrivateRepos() + mUser.getPublicRepos();
        reposRow.setText(getResources().getQuantityString(R.plurals.repository, repoCount, repoCount));
        reposRow.setClickIntent(RepositoryListActivity.makeIntent(getActivity(), mUserLogin, !isUser));

        TextView tvName = mContentView.findViewById(R.id.tv_name);
        String name = StringUtils.isBlank(mUser.getName()) ? mUser.getLogin() : mUser.getName();
        if (ApiHelpers.UserType.ORG.equals(mUser.getType())) {
            tvName.setText(getString(R.string.org_user_template, name));
        } else {
            tvName.setText(name);
        }

        fillTextView(R.id.tv_email, mUser.getEmail());
        fillTextView(R.id.tv_website, mUser.getBlog());
        fillTextView(R.id.tv_company, mUser.getCompany());
        fillTextView(R.id.tv_location, mUser.getLocation());

        getLoaderManager().initLoader(1, null, mRepoListCallback);
        if (isUser) {
            getLoaderManager().initLoader(2, null, mOrganizationCallback);
        } else {
            fillOrganizations(null);
        }
    }

    private void updateFollowingUi() {
        boolean following = mIsFollowing != null && mIsFollowing;
        @PluralsRes int resId = following ? R.plurals.follower_and_self : R.plurals.follower;
        int count = following ? mUser.getFollowers() - 1 : mUser.getFollowers();
        mFollowersRow.setText(getResources().getQuantityString(resId, count, count));
        mFollowersRow.setToggleState(following);
    }

    private void fillTextView(int id, String text) {
        TextView view = mContentView.findViewById(id);
        if (!StringUtils.isBlank(text)) {
            view.setText(text);
            view.setVisibility(View.VISIBLE);
        } else {
            view.setVisibility(View.GONE);
        }
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        Intent intent = null;

        if (id == R.id.btn_repos) {
            intent = RepositoryListActivity.makeIntent(getActivity(), mUserLogin,
                    ApiHelpers.UserType.ORG.equals(mUser.getType()));
        } else if (view.getTag() instanceof Repository) {
            intent = RepositoryActivity.makeIntent(getActivity(), (Repository) view.getTag());
        } else if (view.getTag() instanceof User) {
            intent = UserActivity.makeIntent(getActivity(), (User) view.getTag());
        }
        if (intent != null) {
            startActivity(intent);
        }
    }

    @Override
    public void onIconClick(OverviewRow row) {
        if (mIsFollowing != null) {
            new UpdateFollowTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    private void fillTopRepos(Collection<Repository> topRepos) {
        LinearLayout ll = mContentView.findViewById(R.id.ll_top_repos);
        ll.removeAllViews();

        LayoutInflater inflater = getLayoutInflater();

        if (topRepos != null) {
            for (Repository repo : topRepos) {
                View rowView = inflater.inflate(R.layout.top_repo, null);
                rowView.setOnClickListener(this);
                rowView.setTag(repo);

                TextView tvTitle = rowView.findViewById(R.id.tv_title);
                tvTitle.setText(repo.getOwner().getLogin() + "/" + repo.getName());

                TextView tvDesc = rowView.findViewById(R.id.tv_desc);
                if (!StringUtils.isBlank(repo.getDescription())) {
                    tvDesc.setVisibility(View.VISIBLE);
                    tvDesc.setText(repo.getDescription());
                } else {
                    tvDesc.setVisibility(View.GONE);
                }

                TextView tvForks = rowView.findViewById(R.id.tv_forks);
                tvForks.setText(String.valueOf(repo.getForks()));

                TextView tvStars = rowView.findViewById(R.id.tv_stars);
                tvStars.setText(String.valueOf(repo.getWatchers()));

                ll.addView(rowView);
            }
        }

        View btnMore = getView().findViewById(R.id.btn_repos);
        if (topRepos != null && !topRepos.isEmpty()) {
            btnMore.setOnClickListener(this);
            btnMore.setVisibility(View.VISIBLE);
        } else {
            TextView hintView = (TextView) inflater.inflate(R.layout.hint_view, ll, false);
            hintView.setText(R.string.user_no_repos);
            ll.addView(hintView);
        }

        getView().findViewById(R.id.pb_top_repos).setVisibility(View.GONE);
        getView().findViewById(R.id.ll_top_repos).setVisibility(View.VISIBLE);
    }

    private void fillOrganizations(List<User> organizations) {
        ViewGroup llOrgs = mContentView.findViewById(R.id.ll_orgs);
        LinearLayout llOrg = mContentView.findViewById(R.id.ll_org);
        int count = organizations != null ? organizations.size() : 0;
        LayoutInflater inflater = getLayoutInflater();

        llOrg.removeAllViews();
        llOrgs.setVisibility(count > 0 ? View.VISIBLE : View.GONE);

        for (int i = 0; i < count; i++) {
            User org = organizations.get(i);
            View rowView = inflater.inflate(R.layout.selectable_label_with_avatar, llOrg, false);

            rowView.setOnClickListener(this);
            rowView.setTag(org);

            ImageView avatar = rowView.findViewById(R.id.iv_gravatar);
            AvatarHandler.assignAvatar(avatar, org);

            TextView nameView = rowView.findViewById(R.id.tv_title);
            nameView.setText(org.getLogin());

            llOrg.addView(rowView);
        }
    }

    private class UpdateFollowTask extends BackgroundTask<Void> {
        public UpdateFollowTask() {
            super(UserFragment.this.getActivity());
        }

        @Override
        protected Void run() throws Exception {
            UserService userService = (UserService)
                    Gh4Application.get().getService(Gh4Application.USER_SERVICE);
            if (mIsFollowing) {
                userService.unfollow(mUserLogin);
            } else {
                userService.follow(mUserLogin);
            }
            return null;
        }

        @Override
        protected void onSuccess(Void result) {
            if (mUser != null && mIsFollowing != null) {
                mIsFollowing = !mIsFollowing;
                if (mIsFollowing) {
                    mUser.setFollowers(mUser.getFollowers() + 1);
                } else {
                    mUser.setFollowers(mUser.getFollowers() - 1);
                }
                updateFollowingUi();
            }
        }
    }
}
