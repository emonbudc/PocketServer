#include <stdio.h>
#include <unistd.h>
#include <sys/utsname.h>
#include <time.h>

int main(void) {
    struct utsname info;
    printf("PocketServer native probe\n");
    if (uname(&info) == 0) {
        printf("sysname:  %s\n", info.sysname);
        printf("release:  %s\n", info.release);
        printf("machine:  %s\n", info.machine);
    } else {
        printf("uname:    unavailable\n");
    }
    printf("pid:      %ld\n", (long)getpid());
    printf("uid:      %ld\n", (long)getuid());
    printf("euid:     %ld\n", (long)geteuid());
    long page_size = sysconf(_SC_PAGESIZE);
    printf("page:     %ld bytes\n", page_size);
    printf("exec:     app-private storage, no root\n");
    fflush(stdout);
    return 0;
}
