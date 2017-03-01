package com.tommihirvonen.exifnotes.Adapters;

// Copyright 2015
// Tommi Hirvonen

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.support.v13.app.FragmentPagerAdapter;

import com.tommihirvonen.exifnotes.Fragments.CamerasFragment;
import com.tommihirvonen.exifnotes.Fragments.FiltersFragment;
import com.tommihirvonen.exifnotes.Fragments.LensesFragment;
import com.tommihirvonen.exifnotes.R;

public class PagerAdapter extends FragmentPagerAdapter {

    private static final int PAGE_COUNT = 3;
    private Activity activity;
    private Fragment Lenses, Cameras, Filters;

    public PagerAdapter(FragmentManager fm, Activity activity) {
        super(fm);
        this.activity = activity;
    }

    @Override
    public int getCount() {
        return PAGE_COUNT;
    }

    @Override
    public Fragment getItem(int position) {
//        if ( position == 0 ) return new LensesFragment();
//        if (position == 1 ) return new CamerasFragment();
//        else return null;
        switch (position) {
            case 2:
                if(Filters == null)
                    Filters = new FiltersFragment();
                return Filters;
            case 1:
                if(Lenses == null)
                    Lenses = new LensesFragment();
                return Lenses;
            case 0:
                if(Cameras == null)
                    Cameras = new CamerasFragment();
                return Cameras;
        }
        return null;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        switch (position) {
            case 2:
                return activity.getResources().getString(R.string.Filters);
            case 1:
                return activity.getResources().getString(R.string.Lenses);
            case 0:
                return activity.getResources().getString(R.string.Cameras);
        }
        return null;
    }
}
