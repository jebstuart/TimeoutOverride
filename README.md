TimeoutOverride
===============

Android library for extending the screen timeout for an Activity.  To use:

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        new ScreenTimeoutOverride(20, getWindow());
    }
