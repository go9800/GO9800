package emu98;


import com.sun.jna.Library;

public interface JNAtest extends Library
{
	long test_init();
	int test_set();
	int test_check();
}
