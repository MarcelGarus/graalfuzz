def foo(a):
    if a.foo < 10:
        print("Whoa!")
    if a is None:
        print("Got None")
    elif a < 10:
        print("Hi")
    else:
        print("Else")

foo
