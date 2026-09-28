#include <errno.h>
#include <fcntl.h>
#include <linux/input.h>
#include <linux/uinput.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <time.h>
#include <unistd.h>

static volatile sig_atomic_t g_running = 1;
static const char *PID_FILE = "/data/local/tmp/stra_touch_ace5pro.pid";

static void on_signal(int sig) {
    (void)sig;
    g_running = 0;
}

static void set_event(struct input_event *ev, uint16_t type,
                      uint16_t code, int32_t value) {
    memset(ev, 0, sizeof(*ev));
    ev->type = type;
    ev->code = code;
    ev->value = value;
}

/* One write per contact transition cuts the old 18 syscalls per tap to two. */
static int write_events(int fd, struct input_event *events, size_t count) {
    const unsigned char *p = (const unsigned char *)events;
    size_t left = count * sizeof(*events);
    while (left > 0) {
        ssize_t n = write(fd, p, left);
        if (n < 0) {
            if (errno == EINTR) continue;
            return -1;
        }
        p += n;
        left -= (size_t)n;
    }
    return 0;
}

static int open_uinput(void) {
    const char *paths[] = {"/dev/uinput", "/dev/input/uinput"};
    for (size_t i = 0; i < sizeof(paths) / sizeof(paths[0]); ++i) {
        int fd = open(paths[i], O_WRONLY | O_NONBLOCK | O_CLOEXEC);
        if (fd >= 0) return fd;
    }
    return -1;
}

static int setup_uinput(int fd, int width, int height) {
    if (width < 2 || height < 2) return -1;

    if (ioctl(fd, UI_SET_EVBIT, EV_SYN) < 0) return -1;
    if (ioctl(fd, UI_SET_EVBIT, EV_KEY) < 0) return -1;
    if (ioctl(fd, UI_SET_KEYBIT, BTN_TOUCH) < 0) return -1;
    if (ioctl(fd, UI_SET_KEYBIT, BTN_TOOL_FINGER) < 0) return -1;
    if (ioctl(fd, UI_SET_EVBIT, EV_ABS) < 0) return -1;

    int abs_codes[] = {
        ABS_X, ABS_Y,
        ABS_MT_SLOT, ABS_MT_TRACKING_ID,
        ABS_MT_POSITION_X, ABS_MT_POSITION_Y,
        ABS_MT_TOUCH_MAJOR, ABS_MT_PRESSURE
    };
    for (size_t i = 0; i < sizeof(abs_codes) / sizeof(abs_codes[0]); ++i) {
        if (ioctl(fd, UI_SET_ABSBIT, abs_codes[i]) < 0) return -1;
    }

#ifdef UI_SET_PROPBIT
    ioctl(fd, UI_SET_PROPBIT, INPUT_PROP_DIRECT);
#endif

    struct uinput_user_dev dev;
    memset(&dev, 0, sizeof(dev));
    snprintf(dev.name, UINPUT_MAX_NAME_SIZE, "STRA Virtual Touchscreen");
    dev.id.bustype = BUS_USB;
    dev.id.vendor = 0x5354;
    dev.id.product = 0x5241;
    dev.id.version = 0x0202;

    dev.absmin[ABS_X] = 0;
    dev.absmax[ABS_X] = width - 1;
    dev.absmin[ABS_Y] = 0;
    dev.absmax[ABS_Y] = height - 1;

    dev.absmin[ABS_MT_SLOT] = 0;
    dev.absmax[ABS_MT_SLOT] = 9;
    dev.absmin[ABS_MT_TRACKING_ID] = 0;
    dev.absmax[ABS_MT_TRACKING_ID] = 65535;
    dev.absmin[ABS_MT_POSITION_X] = 0;
    dev.absmax[ABS_MT_POSITION_X] = width - 1;
    dev.absmin[ABS_MT_POSITION_Y] = 0;
    dev.absmax[ABS_MT_POSITION_Y] = height - 1;
    dev.absmin[ABS_MT_TOUCH_MAJOR] = 0;
    dev.absmax[ABS_MT_TOUCH_MAJOR] = 255;
    dev.absmin[ABS_MT_PRESSURE] = 0;
    dev.absmax[ABS_MT_PRESSURE] = 255;

    if (write(fd, &dev, sizeof(dev)) != (ssize_t)sizeof(dev)) return -1;
    if (ioctl(fd, UI_DEV_CREATE) < 0) return -1;

    usleep(350000);
    return 0;
}

static void destroy_uinput(int fd) {
    if (fd >= 0) {
        ioctl(fd, UI_DEV_DESTROY);
        close(fd);
    }
}

static int touch_down(int fd, int x, int y, int tracking_id) {
    struct input_event events[11];
    set_event(&events[0], EV_ABS, ABS_MT_SLOT, 0);
    set_event(&events[1], EV_ABS, ABS_MT_TRACKING_ID, tracking_id);
    set_event(&events[2], EV_ABS, ABS_MT_POSITION_X, x);
    set_event(&events[3], EV_ABS, ABS_MT_POSITION_Y, y);
    set_event(&events[4], EV_ABS, ABS_MT_TOUCH_MAJOR, 8);
    set_event(&events[5], EV_ABS, ABS_MT_PRESSURE, 64);
    set_event(&events[6], EV_ABS, ABS_X, x);
    set_event(&events[7], EV_ABS, ABS_Y, y);
    set_event(&events[8], EV_KEY, BTN_TOOL_FINGER, 1);
    set_event(&events[9], EV_KEY, BTN_TOUCH, 1);
    set_event(&events[10], EV_SYN, SYN_REPORT, 0);
    return write_events(fd, events, 11);
}

static int touch_up(int fd) {
    struct input_event events[7];
    set_event(&events[0], EV_ABS, ABS_MT_SLOT, 0);
    set_event(&events[1], EV_ABS, ABS_MT_TRACKING_ID, -1);
    set_event(&events[2], EV_ABS, ABS_MT_TOUCH_MAJOR, 0);
    set_event(&events[3], EV_ABS, ABS_MT_PRESSURE, 0);
    set_event(&events[4], EV_KEY, BTN_TOUCH, 0);
    set_event(&events[5], EV_KEY, BTN_TOOL_FINGER, 0);
    set_event(&events[6], EV_SYN, SYN_REPORT, 0);
    return write_events(fd, events, 7);
}

static int stop_requested(const char *path) {
    return access(path, F_OK) == 0;
}

static void write_pid_file(void) {
    FILE *f = fopen(PID_FILE, "w");
    if (f) {
        fprintf(f, "%ld\n", (long)getpid());
        fclose(f);
    }
}

static void remove_pid_file(void) {
    FILE *f = fopen(PID_FILE, "r");
    if (f) {
        long pid = -1;
        if (fscanf(f, "%ld", &pid) == 1 && pid == (long)getpid()) {
            unlink(PID_FILE);
        }
        fclose(f);
    }
}

static struct timespec add_us(struct timespec ts, uint64_t us) {
    ts.tv_sec += (time_t)(us / 1000000ULL);
    ts.tv_nsec += (long)((us % 1000000ULL) * 1000ULL);
    if (ts.tv_nsec >= 1000000000L) {
        ts.tv_sec++;
        ts.tv_nsec -= 1000000000L;
    }
    return ts;
}

static int sleep_until(const struct timespec *deadline) {
    int rc;
    do {
        rc = clock_nanosleep(CLOCK_MONOTONIC, TIMER_ABSTIME, deadline, NULL);
    } while (g_running && rc == EINTR);
    return rc == 0 ? 0 : -1;
}

static int probe_only(int width, int height) {
    int fd = open_uinput();
    if (fd < 0) {
        fprintf(stderr, "uinput_open_failed:%s\n", strerror(errno));
        return 11;
    }

    if (setup_uinput(fd, width, height) < 0) {
        fprintf(stderr, "uinput_setup_failed:%s\n", strerror(errno));
        destroy_uinput(fd);
        return 12;
    }

    printf("READY\n");
    fflush(stdout);
    destroy_uinput(fd);
    return 0;
}

int main(int argc, char **argv) {
    signal(SIGTERM, on_signal);
    signal(SIGINT, on_signal);
    signal(SIGHUP, on_signal);

    if (argc == 4 && strcmp(argv[1], "--probe") == 0) {
        return probe_only(atoi(argv[2]), atoi(argv[3]));
    }

    if (argc < 9) {
        fprintf(stderr,
                "usage: %s stop width height hold_us period_us cycles count x y ...\n",
                argv[0]);
        return 2;
    }

    const char *stop_path = argv[1];
    int width = atoi(argv[2]);
    int height = atoi(argv[3]);
    uint64_t hold_us = strtoull(argv[4], NULL, 10);
    uint64_t period_us = strtoull(argv[5], NULL, 10);
    uint64_t cycles = strtoull(argv[6], NULL, 10);
    int count = atoi(argv[7]);

    if (width < 2 || height < 2 || count <= 0 || argc != 8 + count * 2) {
        fprintf(stderr, "invalid_arguments\n");
        return 3;
    }

    /* Fast mode targets a 0.5 ms start-to-start period with a 0.25 ms contact. */
    if (hold_us < 250ULL) hold_us = 250ULL;
    if (period_us < 500ULL) period_us = 500ULL;
    if (period_us < hold_us) period_us = hold_us;
    if (period_us > 2000000ULL) period_us = 2000000ULL;

    int *xy = (int *)calloc((size_t)count * 2U, sizeof(int));
    if (!xy) return 4;

    for (int i = 0; i < count; ++i) {
        int x = atoi(argv[8 + i * 2]);
        int y = atoi(argv[9 + i * 2]);

        if (x < 0) x = 0;
        if (y < 0) y = 0;
        if (x >= width) x = width - 1;
        if (y >= height) y = height - 1;

        xy[i * 2] = x;
        xy[i * 2 + 1] = y;
    }

    int fd = open_uinput();
    if (fd < 0) {
        fprintf(stderr, "uinput_open_failed:%s\n", strerror(errno));
        free(xy);
        return 5;
    }

    if (setup_uinput(fd, width, height) < 0) {
        fprintf(stderr, "uinput_setup_failed:%s\n", strerror(errno));
        destroy_uinput(fd);
        free(xy);
        return 6;
    }

    /* Java clears the old stop file BEFORE launching. Preserve cancellation
       arriving while uinput is being created. */
    if (stop_requested(stop_path)) { destroy_uinput(fd); free(xy); return 0; }
    write_pid_file();
    printf("READY\n");
    fflush(stdout);

    uint64_t round = 0;
    int tracking_id = 1000;

    while (g_running && (cycles == 0 || round < cycles)) {
        for (int i = 0; i < count && g_running; ++i) {
            if (stop_requested(stop_path)) {
                g_running = 0;
                break;
            }

            struct timespec tap_start;
            if (clock_gettime(CLOCK_MONOTONIC, &tap_start) < 0) {
                g_running = 0;
                break;
            }
            struct timespec next_tap = add_us(tap_start, period_us);

            if (touch_down(fd, xy[i * 2], xy[i * 2 + 1], tracking_id++) < 0) {
                fprintf(stderr, "touch_down_failed:%s\n", strerror(errno));
                g_running = 0;
                break;
            }

            struct timespec release_at;
            if (clock_gettime(CLOCK_MONOTONIC, &release_at) < 0) {
                g_running = 0;
                break;
            }
            release_at = add_us(release_at, hold_us);
            if (sleep_until(&release_at) < 0) {
                g_running = 0;
                break;
            }

            if (touch_up(fd) < 0) {
                fprintf(stderr, "touch_up_failed:%s\n", strerror(errno));
                g_running = 0;
                break;
            }

            if (tracking_id > 65000) tracking_id = 1000;

            if (stop_requested(stop_path)) {
                g_running = 0;
                break;
            }

            if (sleep_until(&next_tap) < 0) {
                g_running = 0;
                break;
            }

        }
        ++round;
    }

    touch_up(fd);
    destroy_uinput(fd);
    free(xy);
    unlink(stop_path);
    remove_pid_file();
    return 0;
}
