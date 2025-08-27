/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.service;

public abstract class ServiceWorker<T extends Service> {

    protected final T service;
    protected final ServiceWorkerAware workerAware;

    public ServiceWorker(T service) {
        this.service = service;
        this.workerAware = ServiceWorkerAware.fromNullable(service);
    }

    public void onOpen() {
        service.open();
    }

    public void onClose() {
        service.close();
    }

    public int doWork() {
        int work = 0;

        try {
            work += workerAware.doFirst(work);
            work += doWorkUnit(work);
            work += workerAware.doLast(work);
        } catch (Throwable x) {
            workerAware.onException(x);
        }
        return work;
    }

    protected abstract int doWorkUnit(int workDone);

}
