package com.misanthropy.lumenite;

final class SelfCheck {
    public static void main(String[] args) throws InterruptedException {
        LightWindowCheck.main(args);
        LightSchedulerCheck.run();
    }
}
