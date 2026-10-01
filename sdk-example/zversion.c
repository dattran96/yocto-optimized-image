#include <stdio.h>
#include <zlib.h>

int main(void)
{
    printf("Built with the Yocto SDK, zlib version: %s\n", zlibVersion());
    return 0;
}
