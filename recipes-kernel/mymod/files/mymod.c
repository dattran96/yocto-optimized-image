#include <linux/init.h>
#include <linux/module.h>
#include <linux/moduleparam.h>
#include <linux/utsname.h>

static char *whom = "world";
module_param(whom, charp, 0444);
MODULE_PARM_DESC(whom, "Who to greet");

static int __init mymod_init(void)
{
      pr_info("mymod: hello %s, running on kernel %s\n",
              whom, init_utsname()->release);
      return 0;
}

static void __exit mymod_exit(void)
{
      pr_info("mymod: goodbye %s\n", whom);
}

module_init(mymod_init);
module_exit(mymod_exit);

MODULE_LICENSE("GPL");
MODULE_AUTHOR("Dat Tran");
MODULE_DESCRIPTION("Example out-of-tree module built with Yocto");
