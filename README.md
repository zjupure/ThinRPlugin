## ThinR gradle plugin
* Other languages: [简体中文] (README.zh-cn.md) .



### ThinR plugin introduce
***


This tool will remove all the class in R.java except the styleable class and replace the referance into the constant value. So you can reduce the dex files number and apk size.

The plugin has been used on the mogujie app, the apk size is reduced by 1MB (the original apk size of 40MB), the number of DEX reduced by 3.

### ThinR plugin principle


In the R.java of android project , except the class styleable , all the class fields are int object and will never change the value in the run-time.

So in the compile-time we mark all the fields' name and their values,then use the asm tool to scan all the class files to replace the name into the value.


### HOW TO USE
***
Add the dependency in the build.gralde of the project

 	classpath   'com.mogujie.gradle:ThinRPlugin:0.0.1'
 
Add the following code in the inner gradle file of the module :

	 apply plugin: 'thinR'
	 
	 thinR {
	     // In order not to affect the daily development of compilation speed, debug version can not delete R
	   skipThinRDebug = true
	 }
    
### Licence
***
ThinRPlugin is licensed under the MIT license




In case of using the issue to the wangzhi@meili-inc.com please!